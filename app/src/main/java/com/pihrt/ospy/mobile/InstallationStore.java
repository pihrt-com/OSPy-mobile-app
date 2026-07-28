package com.pihrt.ospy.mobile;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class InstallationStore {
    private final KeystoreStore protectedStore;
    private final List<Installation> installations = new ArrayList<>();

    InstallationStore(Context context) {
        protectedStore = new KeystoreStore(context);
    }

    List<Installation> load() throws Exception {
        installations.clear();
        JSONArray array = new JSONArray(protectedStore.load());
        for (int i = 0; i < array.length(); i++) {
            installations.add(Installation.fromJson(array.getJSONObject(i)));
        }
        return new ArrayList<>(installations);
    }

    void upsert(Installation installation) throws Exception {
        for (int i = 0; i < installations.size(); i++) {
            if (installations.get(i).id.equals(installation.id)) {
                installations.set(i, installation);
                persist();
                return;
            }
        }
        installations.add(installation);
        persist();
    }

    void remove(String id) throws Exception {
        installations.removeIf(item -> item.id.equals(id));
        persist();
    }

    private void persist() throws Exception {
        JSONArray array = new JSONArray();
        for (Installation item : installations) array.put(item.toJson());
        protectedStore.save(array.toString());
    }
}

