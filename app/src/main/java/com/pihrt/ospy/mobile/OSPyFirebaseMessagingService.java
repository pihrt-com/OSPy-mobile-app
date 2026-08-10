package com.pihrt.ospy.mobile;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Map;

public final class OSPyFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        PushRegistrationManager.syncAll(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> values = message.getData();
        if (!"ospy_notification".equals(values.get("type"))) return;
        String instanceId = text(values, "instance_id");
        String deviceId = new PushStateStore(this).deviceForInstance(instanceId);
        if (deviceId.isEmpty()) return;

        try {
            Installation installation = new InstallationStore(this).latest(deviceId);
            if (installation == null) return;
            JSONObject notification = new JSONObject()
                    .put("id", text(values, "notification_id"))
                    .put("type", text(values, "event_type"))
                    .put("severity", text(values, "severity"))
                    .put("code", text(values, "code"))
                    .put("title", text(values, "title"))
                    .put("message", text(values, "message"));
            String dataJson = text(values, "data_json");
            notification.put("data", dataJson.isEmpty()
                    ? new JSONObject() : new JSONObject(dataJson));
            new NotificationCenter(this).showServerNotification(
                    installation, notification);
        } catch (Exception ignored) {
            // A malformed or stale data message must never crash the service.
        }
    }

    private static String text(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }
}
