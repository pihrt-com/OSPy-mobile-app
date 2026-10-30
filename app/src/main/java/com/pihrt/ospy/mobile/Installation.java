package com.pihrt.ospy.mobile;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Locale;

final class Installation {
    String id;
    String name;
    String baseUrl;
    String username;
    String refreshToken;
    boolean allowUnverifiedCertificate;

    Installation(String id, String name, String baseUrl, String username,
                 String refreshToken, boolean allowUnverifiedCertificate) {
        this.id = id;
        this.name = name;
        this.baseUrl = normalize(baseUrl);
        this.username = username;
        this.refreshToken = refreshToken;
        this.allowUnverifiedCertificate = allowUnverifiedCertificate;
    }

    static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        if (!result.contains("://") && !result.isEmpty()) {
            result = "https://" + result;
        }
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        if (result.endsWith("/api/v1")) result = result.substring(0, result.length() - 7);
        return result;
    }

    static boolean isValidBaseUrl(String value) {
        try {
            URI uri = URI.create(normalize(value));
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) ||
                    "https".equalsIgnoreCase(scheme)) &&
                    uri.getHost() != null && !uri.getHost().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean isPrivateAddress() {
        return needsLocalNetworkAccess(baseUrl);
    }

    static boolean needsLocalNetworkAccess(String value) {
        try {
            String host = URI.create(normalize(value)).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            if ("::1".equals(host)) return true;
            if (host.contains(":") &&
                    (host.startsWith("fc") || host.startsWith("fd"))) return true;
            if (host.contains(":") && host.startsWith("fe") && host.length() > 2) {
                int secondNibble = Character.digit(host.charAt(2), 16);
                if (secondNibble >= 8 && secondNibble <= 11) return true;
            }
            if ("localhost".equals(host) || host.startsWith("127.")) return true;
            if (host.startsWith("10.") || host.startsWith("192.168.") ||
                    host.startsWith("169.254.")) return true;
            if (host.startsWith("172.")) {
                String[] parts = host.split("\\.");
                if (parts.length > 1) {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                }
            }
            return host.endsWith(".local") || !host.contains(".");
        } catch (Exception ignored) {
            // An invalid URL is handled by the connection screen.
        }
        return false;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("base_url", baseUrl)
                .put("username", username)
                .put("refresh_token", refreshToken)
                .put("allow_unverified_certificate", allowUnverifiedCertificate);
    }

    static Installation fromJson(JSONObject value) throws JSONException {
        return new Installation(
                value.getString("id"),
                value.optString("name", "OSPy"),
                value.getString("base_url"),
                value.optString("username", ""),
                value.getString("refresh_token"),
                value.optBoolean("allow_unverified_certificate", false)
        );
    }
}
