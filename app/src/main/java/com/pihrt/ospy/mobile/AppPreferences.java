package com.pihrt.ospy.mobile;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPreferences {
    private static final String FILE = "ospy_app_preferences";
    private final SharedPreferences values;

    AppPreferences(Context context) {
        values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean watchNetwork() {
        return values.getBoolean("watch_network", false);
    }

    void setWatchNetwork(boolean enabled) {
        values.edit().putBoolean("watch_network", enabled).apply();
    }

    boolean openLastInstallation() {
        return values.getBoolean("open_last_installation", false);
    }

    void setOpenLastInstallation(boolean enabled) {
        values.edit().putBoolean("open_last_installation", enabled).apply();
    }

    String lastInstallationId() {
        return values.getString("last_installation_id", "");
    }

    void setLastInstallationId(String id) {
        values.edit().putString("last_installation_id", id == null ? "" : id).apply();
    }
}
