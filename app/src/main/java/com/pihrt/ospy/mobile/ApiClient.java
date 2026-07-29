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
    interface Callback {
        void success(JSONObject response);
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

    void pair(String username, String password, String twoFactor, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject device = new JSONObject()
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

    private void refresh() throws Exception {
        JSONObject body = new JSONObject()
                .put("refresh_token", installation.refreshToken);
        JSONObject envelope = executeRaw("POST", "/auth/refresh", body, "");
        JSONObject data = envelope.getJSONObject("data");
        accessToken = data.getString("access_token");
        installation.refreshToken = data.getString("refresh_token");
        store.upsert(installation);
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
        if (source == null) return "";
        try (InputStream input = source;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String message(Exception error) {
        String value = error.getMessage();
        return value == null || value.isEmpty()
                ? error.getClass().getSimpleName() : value;
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
