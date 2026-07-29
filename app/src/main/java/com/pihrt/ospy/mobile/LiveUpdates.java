package com.pihrt.ospy.mobile;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

final class LiveUpdates {
    interface Listener {
        void event(JSONObject event);
    }

    private final ApiClient client;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastEventId;
    private boolean synchronizedWithServer;
    private boolean running;

    LiveUpdates(ApiClient client, Listener listener) {
        this.client = client;
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        poll();
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void poll() {
        if (!running) return;
        client.request("GET", "/changes?after=" + lastEventId, null,
                new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        JSONArray events = response.optJSONArray("data");
                        if (events != null) {
                            for (int i = 0; i < events.length(); i++) {
                                JSONObject event = events.optJSONObject(i);
                                if (event == null) continue;
                                lastEventId = Math.max(
                                        lastEventId, event.optInt("id", lastEventId));
                                // The first response only establishes the
                                // cursor. Buffered historical notifications
                                // must not appear as new after login.
                                if (synchronizedWithServer) listener.event(event);
                            }
                        }
                        synchronizedWithServer = true;
                        schedule(3000);
                    }

                    @Override public void failure(String message) {
                        schedule(10000);
                    }
                });
    }

    private void schedule(long delay) {
        if (running) handler.postDelayed(this::poll, delay);
    }
}
