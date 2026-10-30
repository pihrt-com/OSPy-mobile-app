package com.pihrt.ospy.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

final class RestoreMetadataStore {
    private static final String FILE = "ospy_restore_metadata";
    private final SharedPreferences values;

    RestoreMetadataStore(Context context) {
        values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    void save(Installation installation) {
        values.edit()
                .putString("name", installation.name)
                .putString("base_url", installation.baseUrl)
                .putString("username", installation.username)
                .putBoolean(
                        "allow_unverified_certificate",
                        installation.allowUnverifiedCertificate)
                .apply();
    }

    Installation restoredInstallation() {
        String baseUrl = values.getString("base_url", "");
        String username = values.getString("username", "");
        if (!Installation.isValidBaseUrl(baseUrl) || username.trim().isEmpty()) {
            return null;
        }
        return new Installation(
                UUID.randomUUID().toString(),
                values.getString("name", "OSPy"),
                baseUrl,
                username,
                "",
                values.getBoolean("allow_unverified_certificate", false));
    }

    void clear() {
        values.edit().clear().apply();
    }
}
