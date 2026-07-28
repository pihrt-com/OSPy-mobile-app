package com.pihrt.ospy.mobile;

import org.json.JSONException;
import org.json.JSONObject;

final class Installation {
    String id;
    String name;
    String baseUrl;
    String username;
    String refreshToken;

    Installation(String id, String name, String baseUrl, String username,
                 String refreshToken) {
        this.id = id;
        this.name = name;
        this.baseUrl = normalize(baseUrl);
        this.username = username;
        this.refreshToken = refreshToken;
    }

    static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        if (result.endsWith("/api/v1")) result = result.substring(0, result.length() - 7);
        return result;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("base_url", baseUrl)
                .put("username", username)
                .put("refresh_token", refreshToken);
    }

    static Installation fromJson(JSONObject value) throws JSONException {
        return new Installation(
                value.getString("id"),
                value.optString("name", "OSPy"),
                value.getString("base_url"),
                value.optString("username", ""),
                value.getString("refresh_token")
        );
    }
}

