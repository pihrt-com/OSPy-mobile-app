package com.pihrt.ospy.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class PushRegistrationManager {
    private static final String TAG = "OSPyPush";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final List<Runnable> COMPLETION_CALLBACKS = new ArrayList<>();
    private static volatile boolean rerun;

    private PushRegistrationManager() {
    }

    static void syncAll(Context context) {
        syncAll(context, null);
    }

    static void syncAll(Context context, Runnable completion) {
        Context application = context.getApplicationContext();
        if (completion != null) {
            synchronized (COMPLETION_CALLBACKS) {
                COMPLETION_CALLBACKS.add(completion);
            }
        }
        if (!RUNNING.compareAndSet(false, true)) {
            rerun = true;
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                do {
                    rerun = false;
                    synchronize(application);
                } while (rerun);
            } finally {
                RUNNING.set(false);
                if (rerun) syncAll(application);
                else dispatchCompletions();
            }
        });
    }

    static void clearLocalState(Context context, String deviceId) {
        new PushStateStore(context).remove(deviceId);
        new PushSyncStatusStore(context).remove(deviceId);
    }

    private static void synchronize(Context context) {
        InstallationStore store = new InstallationStore(context);
        final List<Installation> installations;
        PushSyncStatusStore syncStatus = new PushSyncStatusStore(context);
        try {
            installations = store.load();
        } catch (Exception error) {
            Log.w(TAG, "Cannot load installations for push synchronization", error);
            return;
        }
        if (installations.isEmpty()) return;

        final String fcmToken;
        try {
            for (Installation installation : installations) {
                syncStatus.progress(installation.id, PushSyncStatusStore.FCM);
            }
            fcmToken = Tasks.await(
                    FirebaseMessaging.getInstance().getToken(),
                    30, TimeUnit.SECONDS);
            if (fcmToken == null || fcmToken.isEmpty()) {
                throw new IllegalStateException("empty_fcm_token");
            }
        } catch (Exception error) {
            recordTokenError(syncStatus, installations,
                    PushSyncStatusStore.FCM, error);
            return;
        }

        final String appCheckToken;
        try {
            for (Installation installation : installations) {
                syncStatus.progress(
                        installation.id, PushSyncStatusStore.APP_CHECK);
            }
            AppCheckToken result = Tasks.await(
                    FirebaseAppCheck.getInstance().getAppCheckToken(false),
                    30, TimeUnit.SECONDS);
            appCheckToken = result.getToken();
            if (appCheckToken == null || appCheckToken.isEmpty()) {
                throw new IllegalStateException("empty_app_check_token");
            }
        } catch (Exception error) {
            recordTokenError(syncStatus, installations,
                    PushSyncStatusStore.APP_CHECK, error);
            return;
        }

        PushStateStore state = new PushStateStore(context);
        for (Installation installation : installations) {
            try {
                syncInstallation(context, store, state, syncStatus, installation,
                        fcmToken, appCheckToken);
            } catch (Exception error) {
                PushSyncStatusStore.Snapshot previous =
                        syncStatus.load(installation.id);
                String stage = PushSyncStatusStore.ERROR.equals(previous.state)
                        ? previous.stage : previous.state;
                syncStatus.error(
                        installation.id, stage, technicalDetail(error));
                Log.w(TAG, "Push registration failed at " + stage, error);
                // An offline or older OSPy installation must not prevent the
                // remaining installations from registering.
            }
        }
    }

    private static void syncInstallation(
            Context context, InstallationStore store, PushStateStore state,
            PushSyncStatusStore syncStatus, Installation installation,
            String fcmToken, String appCheckToken)
            throws Exception {
        ApiClient client = new ApiClient(installation, store);
        syncStatus.progress(installation.id, PushSyncStatusStore.OSPY);
        JSONObject push = client.requestBlocking("GET", "/push", null)
                .getJSONObject("data");
        String relayUrl = normalizeRelayUrl(push.optString("relay_url", ""));
        if (!push.optBoolean("enabled", false) ||
                !push.optBoolean("configured", false) || relayUrl.isEmpty()) {
            syncStatus.progress(
                    installation.id, PushSyncStatusStore.DISABLED);
            return;
        }

        JSONObject server = client.requestBlocking("GET", "/server", null)
                .getJSONObject("data");
        String instanceId = server.optString("instance_id", "").trim();
        if (instanceId.isEmpty()) return;

        JSONObject subscription = push.optJSONObject("subscription");
        boolean bindingCurrent = subscription != null &&
                state.matches(installation.id, instanceId, relayUrl, fcmToken);
        if (!bindingCurrent) {
            syncStatus.progress(installation.id, PushSyncStatusStore.RELAY);
            JSONObject registration = register(
                    relayUrl, appCheckToken, fcmToken, instanceId,
                    installation.id);
            JSONObject body = new JSONObject()
                    .put("subscription_id", registration.getString("subscription_id"))
                    .put("send_secret", registration.getString("send_secret"))
                    .put("enabled", new NotificationCenter(context).isEnabled())
                    .put("categories", enabledCategories(context));
            syncStatus.progress(installation.id, PushSyncStatusStore.SAVE);
            client.requestBlocking("POST", "/push", body);
            state.save(installation.id, instanceId, relayUrl, fcmToken);
            syncStatus.progress(installation.id, PushSyncStatusStore.READY);
            return;
        }

        JSONObject preferences = new JSONObject()
                .put("enabled", new NotificationCenter(context).isEnabled())
                .put("categories", enabledCategories(context));
        syncStatus.progress(installation.id, PushSyncStatusStore.SAVE);
        client.requestBlocking("PUT", "/push", preferences);
        syncStatus.progress(installation.id, PushSyncStatusStore.READY);
    }

    private static JSONObject register(
            String relayUrl, String appCheckToken, String fcmToken,
            String instanceId, String deviceId) throws Exception {
        JSONObject body = new JSONObject()
                .put("fcm_token", fcmToken)
                .put("instance_id", instanceId)
                .put("device_id", deviceId)
                .put("app_version", BuildConfig.VERSION_NAME);
        byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(
                relayUrl + "/v1/register").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("X-Firebase-AppCheck", appCheckToken);
        connection.setFixedLengthStreamingMode(encoded.length);
        try (java.io.OutputStream output = connection.getOutputStream()) {
            output.write(encoded);
        }
        int status = connection.getResponseCode();
        InputStream source = status >= 400
                ? connection.getErrorStream() : connection.getInputStream();
        String response = read(source);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String code = "";
            try {
                code = new JSONObject(response).optString("error", "");
            } catch (Exception ignored) {
                // The relay may return an empty or non-JSON proxy response.
            }
            throw new RelayException(status, code);
        }
        return new JSONObject(response);
    }

    private static void recordTokenError(
            PushSyncStatusStore status, List<Installation> installations,
            String stage, Exception error) {
        String detail = technicalDetail(error);
        for (Installation installation : installations) {
            status.error(installation.id, stage, detail);
        }
        Log.w(TAG, "Push registration failed at " + stage, error);
    }

    private static String technicalDetail(Exception error) {
        Throwable value = error;
        while (value.getCause() != null && value.getCause() != value) {
            value = value.getCause();
        }
        if (value instanceof ApiClient.ApiException) {
            ApiClient.ApiException api = (ApiClient.ApiException) value;
            return "HTTP " + api.status +
                    (api.code == null || api.code.isEmpty()
                            ? "" : " · " + api.code);
        }
        if (value instanceof RelayException) {
            RelayException relay = (RelayException) value;
            return "HTTP " + relay.status +
                    (relay.code.isEmpty() ? "" : " · " + relay.code);
        }
        return value.getClass().getSimpleName();
    }

    private static void dispatchCompletions() {
        List<Runnable> callbacks;
        synchronized (COMPLETION_CALLBACKS) {
            callbacks = new ArrayList<>(COMPLETION_CALLBACKS);
            COMPLETION_CALLBACKS.clear();
        }
        Handler main = new Handler(Looper.getMainLooper());
        for (Runnable callback : callbacks) main.post(callback);
    }

    private static final class RelayException extends Exception {
        final int status;
        final String code;

        RelayException(int status, String code) {
            super("Push relay HTTP " + status);
            this.status = status;
            this.code = code == null ? "" : code;
        }
    }

    private static JSONArray enabledCategories(Context context) {
        NotificationCenter center = new NotificationCenter(context);
        String[] values = {
                NotificationCenter.CATEGORY_STATION_STARTED,
                NotificationCenter.CATEGORY_STATION_STOPPED,
                NotificationCenter.CATEGORY_RAIN,
                NotificationCenter.CATEGORY_DIAGNOSTICS,
                NotificationCenter.CATEGORY_UPDATES,
                NotificationCenter.CATEGORY_OTHER,
        };
        JSONArray result = new JSONArray();
        for (String value : values) {
            if (center.isCategoryEnabled(value)) result.put(value);
        }
        if (result.length() == 0) {
            for (String value : values) result.put(value);
        }
        return result;
    }

    private static String normalizeRelayUrl(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                    uri.getHost() == null || uri.getHost().isEmpty()) return "";
            return normalized;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String read(InputStream source) throws Exception {
        if (source == null) return "";
        try (InputStream input = source;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
