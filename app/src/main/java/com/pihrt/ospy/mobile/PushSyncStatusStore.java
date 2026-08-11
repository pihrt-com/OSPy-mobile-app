package com.pihrt.ospy.mobile;

import android.content.Context;
import android.content.SharedPreferences;

final class PushSyncStatusStore {
    static final String NEVER = "never";
    static final String FCM = "fcm";
    static final String APP_CHECK = "app_check";
    static final String OSPY = "ospy";
    static final String RELAY = "relay";
    static final String SAVE = "save";
    static final String READY = "ready";
    static final String DISABLED = "disabled";
    static final String ERROR = "error";

    private static final String FILE = "ospy_push_sync_status";
    private final SharedPreferences preferences;

    PushSyncStatusStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                FILE, Context.MODE_PRIVATE);
    }

    void progress(String deviceId, String stage) {
        save(deviceId, stage, "", "");
    }

    void error(String deviceId, String stage, String detail) {
        save(deviceId, ERROR, stage, detail);
    }

    Snapshot load(String deviceId) {
        String prefix = prefix(deviceId);
        return new Snapshot(
                preferences.getString(prefix + "state", NEVER),
                preferences.getString(prefix + "stage", ""),
                preferences.getString(prefix + "detail", ""),
                preferences.getLong(prefix + "updated", 0L));
    }

    void remove(String deviceId) {
        String prefix = prefix(deviceId);
        preferences.edit()
                .remove(prefix + "state")
                .remove(prefix + "stage")
                .remove(prefix + "detail")
                .remove(prefix + "updated")
                .apply();
    }

    private void save(String deviceId, String state, String stage,
                      String detail) {
        String prefix = prefix(deviceId);
        preferences.edit()
                .putString(prefix + "state", state)
                .putString(prefix + "stage", stage)
                .putString(prefix + "detail", detail == null ? "" : detail)
                .putLong(prefix + "updated", System.currentTimeMillis())
                .apply();
    }

    private static String prefix(String deviceId) {
        return (deviceId == null ? "" : deviceId) + "_";
    }

    static final class Snapshot {
        final String state;
        final String stage;
        final String detail;
        final long updated;

        Snapshot(String state, String stage, String detail, long updated) {
            this.state = state;
            this.stage = stage;
            this.detail = detail;
            this.updated = updated;
        }
    }
}
