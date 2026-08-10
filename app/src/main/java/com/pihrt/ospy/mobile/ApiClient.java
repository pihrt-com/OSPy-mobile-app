package com.pihrt.ospy.mobile;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class ApiClient {
    private static final Object REFRESH_LOCK = new Object();

    interface Callback {
        void success(JSONObject response);
        void failure(String message);
    }

    interface DownloadCallback {
        void success(byte[] data);
        void failure(String message);
    }

    private final Installation installation;
    private final InstallationStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String accessToken = "";
    private String pendingChallengeId = "";

    ApiClient(Installation installation, InstallationStore store) {
        this.installation = installation;
        this.store = store;
    }

    void request(String method, String path, JSONObject body, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject response = execute(method, path, body, true);
                main.post(() -> callback.success(response));
            } catch (Exception error) {
                main.post(() -> callback.failure(message(error)));
            }
        });
    }

    void download(String path, DownloadCallback callback) {
        executor.execute(() -> {
            try {
                byte[] response = executeDownload(path, true);
                main.post(() -> callback.success(response));
            } catch (Exception error) {
                main.post(() -> callback.failure(message(error)));
            }
        });
    }

    void probe(Callback callback) {
        request("GET", "/server", null, callback);
    }

    /** Package-private synchronous request for JobService background polling. */
    JSONObject requestBlocking(String method, String path, JSONObject body)
            throws Exception {
        return execute(method, path, body, true);
    }

    void pair(String username, String password, String twoFactor, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject device = new JSONObject()
                        .put("id", installation.id)
                        .put("name", android.os.Build.MODEL)
                        .put("app_version", BuildConfig.VERSION_NAME);
                JSONObject body = new JSONObject()
                        .put("username", username)
                        .put("password", password)
                        .put("device", device);
                if (!twoFactor.trim().isEmpty()) body.put("two_factor_code", twoFactor.trim());
                if (!pendingChallengeId.isEmpty()) {
                    body.put("challenge_id", pendingChallengeId);
                }
                JSONObject envelope = executeRaw("POST", "/auth/login", body, "");
                JSONObject data = envelope.getJSONObject("data");
                pendingChallengeId = "";
                accessToken = data.getString("access_token");
                installation.id = data.getString("device_id");
                installation.username = username;
                installation.refreshToken = data.getString("refresh_token");
                JSONObject server = executeRaw("GET", "/server", null, "");
                installation.name = server.getJSONObject("data").optString(
                        "name", installation.baseUrl);
                store.upsert(installation);
                main.post(() -> callback.success(envelope));
            } catch (ApiException error) {
                if ("two_factor_required".equals(error.code) &&
                        error.details != null) {
                    pendingChallengeId = error.details.optString("challenge_id", "");
                }
                main.post(() -> callback.failure(message(error)));
            } catch (Exception error) {
                main.post(() -> callback.failure(message(error)));
            }
        });
    }

    String token() {
        return accessToken;
    }

    private JSONObject execute(String method, String path, JSONObject body,
                               boolean retry) throws Exception {
        if (accessToken.isEmpty()) refresh();
        try {
            return executeRaw(method, path, body, accessToken);
        } catch (ApiException error) {
            if (retry && error.status == 401) {
                refresh();
                return executeRaw(method, path, body, accessToken);
            }
            throw error;
        }
    }

    private byte[] executeDownload(String path, boolean retry) throws Exception {
        if (accessToken.isEmpty()) refresh();
        try {
            return executeDownloadRaw(path, accessToken);
        } catch (ApiException error) {
            if (retry && error.status == 401) {
                refresh();
                return executeDownloadRaw(path, accessToken);
            }
            throw error;
        }
    }

    private byte[] executeDownloadRaw(String path, String bearer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                installation.baseUrl + "/api/v1" + path).openConnection();
        if (connection instanceof HttpsURLConnection &&
                installation.allowUnverifiedCertificate) {
            configureUnverifiedHttps((HttpsURLConnection) connection);
        }
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/zip");
        connection.setRequestProperty("Authorization", "Bearer " + bearer);
        int status = connection.getResponseCode();
        InputStream source = status >= 400
                ? connection.getErrorStream() : connection.getInputStream();
        byte[] data = readBytes(source);
        connection.disconnect();
        if (status >= 400) {
            String text = new String(data, StandardCharsets.UTF_8);
            JSONObject response = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            JSONObject error = response.optJSONObject("error");
            throw new ApiException(
                    status,
                    error == null ? "" : error.optString("code"),
                    error == null ? "HTTP " + status
                            : error.optString("message", "HTTP " + status),
                    error == null ? null : error.optJSONObject("details"));
        }
        return data;
    }

    private void refresh() throws Exception {
        synchronized (REFRESH_LOCK) {
            // A JobService and the foreground Activity can both need a token.
            // Refresh tokens rotate, so always re-read the newest persisted
            // token after obtaining the process-wide refresh lock.
            Installation latest = store.latest(installation.id);
            if (latest != null && latest.refreshToken != null &&
                    !latest.refreshToken.isEmpty()) {
                installation.refreshToken = latest.refreshToken;
            }
            JSONObject body = new JSONObject()
                    .put("refresh_token", installation.refreshToken);
            JSONObject envelope = executeRaw("POST", "/auth/refresh", body, "");
            JSONObject data = envelope.getJSONObject("data");
            accessToken = data.getString("access_token");
            installation.refreshToken = data.getString("refresh_token");
            store.upsert(installation);
        }
    }

    private JSONObject executeRaw(String method, String path, JSONObject body,
                                  String bearer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                installation.baseUrl + "/api/v1" + path).openConnection();
        if (connection instanceof HttpsURLConnection &&
                installation.allowUnverifiedCertificate) {
            configureUnverifiedHttps((HttpsURLConnection) connection);
        }
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        if (!bearer.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream source = status >= 400
                ? connection.getErrorStream() : connection.getInputStream();
        String text = read(source);
        connection.disconnect();
        JSONObject response = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        if (status >= 400) {
            JSONObject error = response.optJSONObject("error");
            throw new ApiException(
                    status,
                    error == null ? "" : error.optString("code"),
                    error == null ? "HTTP " + status
                            : error.optString("message", "HTTP " + status),
                    error == null ? null : error.optJSONObject("details"));
        }
        return response;
    }

    private static void configureUnverifiedHttps(HttpsURLConnection connection)
            throws Exception {
        TrustManager[] trustManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    @Override public void checkClientTrusted(
                            X509Certificate[] chain, String authType) {
                    }
                    @Override public void checkServerTrusted(
                            X509Certificate[] chain, String authType) {
                    }
                }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        connection.setSSLSocketFactory(context.getSocketFactory());
        connection.setHostnameVerifier((hostname, session) -> true);
    }

    private static String read(InputStream source) throws Exception {
        return new String(readBytes(source), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(InputStream source) throws Exception {
        if (source == null) return new byte[0];
        try (InputStream input = source;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static String message(Exception error) {
        String value = error.getMessage();
        value = value == null || value.isEmpty()
                ? error.getClass().getSimpleName() : value;
        if (error instanceof ApiException) {
            ApiException apiError = (ApiException) error;
            String code = apiError.code;
            if (code != null && !code.isEmpty()) {
                String reason = apiError.details == null
                        ? "" : apiError.details.optString("reason", "");
                return "@api:" + code + ":" + value +
                        (reason.isEmpty() ? "" : "\n@reason:" + reason);
            }
        }
        return value;
    }

    static String errorCode(String value) {
        if (value == null || !value.startsWith("@api:")) return "";
        int separator = value.indexOf(':', 5);
        return separator < 0 ? "" : value.substring(5, separator);
    }

    static String errorMessage(String value) {
        if (value == null) return "";
        if (!value.startsWith("@api:")) return value;
        int separator = value.indexOf(':', 5);
        String message = separator < 0 ? value : value.substring(separator + 1);
        int reason = message.indexOf("\n@reason:");
        return reason < 0 ? message : message.substring(0, reason);
    }

    static String errorReason(String value) {
        if (value == null) return "";
        int reason = value.indexOf("\n@reason:");
        return reason < 0 ? "" : value.substring(reason + 9);
    }

    static final class ApiException extends Exception {
        final int status;
        final String code;
        final JSONObject details;
        ApiException(int status, String code, String message, JSONObject details) {
            super(message);
            this.status = status;
            this.code = code;
            this.details = details;
        }
    }
}
