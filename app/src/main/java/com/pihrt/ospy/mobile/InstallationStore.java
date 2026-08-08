package com.pihrt.ospy.mobile;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class InstallationStore {
    private static final Object STORE_LOCK = new Object();

    private final KeystoreStore protectedStore;
    private final List<Installation> installations = new ArrayList<>();

    InstallationStore(Context context) {
        protectedStore = new KeystoreStore(context);
    }

    List<Installation> load() throws Exception {
        synchronized (STORE_LOCK) {
            loadUnlocked();
            return new ArrayList<>(installations);
        }
    }

    Installation latest(String id) throws Exception {
        synchronized (STORE_LOCK) {
            loadUnlocked();
            for (Installation item : installations) {
                if (item.id.equals(id)) return item;
            }
            return null;
        }
    }

    void upsert(Installation installation) throws Exception {
        synchronized (STORE_LOCK) {
            // Always merge into the newest encrypted store. A background token
            // refresh must never overwrite an edit made by the foreground UI.
            loadUnlocked();
            for (int i = 0; i < installations.size(); i++) {
                if (installations.get(i).id.equals(installation.id)) {
                    installations.set(i, installation);
                    persistUnlocked();
                    return;
                }
            }
            installations.add(installation);
            persistUnlocked();
        }
    }

    void updateMetadata(Installation changed) throws Exception {
        synchronized (STORE_LOCK) {
            // The foreground editor may hold an object loaded before a
            // background refresh rotated the one-time refresh token. Merge
            // editable fields into the newest stored credentials atomically.
            loadUnlocked();
            for (int i = 0; i < installations.size(); i++) {
                Installation latest = installations.get(i);
                if (latest.id.equals(changed.id)) {
                    installations.set(i, new Installation(
                            latest.id, changed.name, changed.baseUrl,
                            latest.username, latest.refreshToken,
                            changed.allowUnverifiedCertificate));
                    persistUnlocked();
                    return;
                }
            }
            installations.add(changed);
            persistUnlocked();
        }
    }

    void remove(String id) throws Exception {
        synchronized (STORE_LOCK) {
            loadUnlocked();
            installations.removeIf(item -> item.id.equals(id));
            persistUnlocked();
        }
    }

    private void loadUnlocked() throws Exception {
        installations.clear();
        JSONArray array = new JSONArray(protectedStore.load());
        for (int i = 0; i < array.length(); i++) {
            installations.add(Installation.fromJson(array.getJSONObject(i)));
        }
    }

    private void persistUnlocked() throws Exception {
        JSONArray array = new JSONArray();
        for (Installation item : installations) array.put(item.toJson());
        protectedStore.save(array.toString());
    }
}
