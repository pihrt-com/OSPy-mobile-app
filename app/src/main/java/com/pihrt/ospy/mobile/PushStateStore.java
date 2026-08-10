package com.pihrt.ospy.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class PushStateStore {
    private static final String FILE = "ospy_push_state";
    private final SharedPreferences preferences;

    PushStateStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                FILE, Context.MODE_PRIVATE);
    }

    boolean matches(String deviceId, String instanceId, String relayUrl,
                    String fcmToken) {
        String prefix = prefix(deviceId);
        return instanceId.equals(preferences.getString(prefix + "instance", "")) &&
                relayUrl.equals(preferences.getString(prefix + "relay", "")) &&
                tokenHash(fcmToken).equals(
                        preferences.getString(prefix + "token_hash", ""));
    }

    void save(String deviceId, String instanceId, String relayUrl,
              String fcmToken) {
        String prefix = prefix(deviceId);
        preferences.edit()
                .putString(prefix + "instance", instanceId)
                .putString(prefix + "relay", relayUrl)
                .putString(prefix + "token_hash", tokenHash(fcmToken))
                .apply();
    }

    String deviceForInstance(String instanceId) {
        for (String key : preferences.getAll().keySet()) {
            if (!key.endsWith("_instance")) continue;
            if (instanceId.equals(preferences.getString(key, ""))) {
                return key.substring(0, key.length() - "_instance".length());
            }
        }
        return "";
    }

    void remove(String deviceId) {
        String prefix = prefix(deviceId);
        preferences.edit()
                .remove(prefix + "instance")
                .remove(prefix + "relay")
                .remove(prefix + "token_hash")
                .apply();
    }

    private static String prefix(String deviceId) {
        return (deviceId == null ? "" : deviceId) + "_";
    }

    private static String tokenHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
