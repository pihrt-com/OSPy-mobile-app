package com.pihrt.ospy.mobile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class NotificationCenter {
    static final String CATEGORY_STATION_STARTED = "station_started";
    static final String CATEGORY_STATION_STOPPED = "station_stopped";
    static final String CATEGORY_RAIN = "rain";
    static final String CATEGORY_DIAGNOSTICS = "diagnostics";
    static final String CATEGORY_UPDATES = "updates";
    static final String CATEGORY_OTHER = "other";

    private static final String CHANNEL = "ospy_events";
    private static final String FILE = "ospy_mobile_preferences";
    private static final String CURSOR_PREFIX = "notification_cursor_";
    private static final long SERVER_FALLBACK_SUPPRESSION_MS = 30_000L;
    private static final long INITIAL_RECENT_WINDOW_MS = 5L * 60L * 1000L;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);
    private static final Object SERVER_LOCK = new Object();
    private static final Map<String, Long> RECENT_SERVER_CATEGORY =
            new ConcurrentHashMap<>();

    private final Context context;
    private final NotificationManager manager;
    private final SharedPreferences preferences;

    NotificationCenter(Context context) {
        this.context = context.getApplicationContext();
        manager = this.context.getSystemService(NotificationManager.class);
        preferences = this.context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    this.context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(
                    this.context.getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);
        }
    }

    /** Show a locally derived fallback event. */
    void show(String category, String title, String message) {
        if (recentServerCategory(category)) return;
        showInternal(NEXT_ID.incrementAndGet(), category, title, message, "");
    }

    /** Show a locally generated notification with a caller supplied id. */
    void show(int id, String category, String title, String message) {
        if (recentServerCategory(category)) return;
        showInternal(id, category, title, message, "");
    }

    /**
     * Process one live notification from API v1. Server ids are persisted per
     * installation so a later background poll cannot show the same event again.
     */
    void showServerNotification(Installation installation, JSONObject data) {
        synchronized (SERVER_LOCK) {
            showServerNotificationLocked(installation, data);
        }
    }

    private void showServerNotificationLocked(
            Installation installation, JSONObject data) {
        if (installation == null || data == null) return;
        long serverId = data.optLong("id", 0L);
        long cursor = serverCursor(installation.id);
        if (serverId > 0 && serverId <= cursor) return;

        String category = categoryForServerNotification(data);
        rememberServerCategory(category);
        String[] localized = localizedServerNotification(data, category);
        String title = localized[0];
        String message = localized[1];
        int notificationId = serverId > 0
                ? stableNotificationId(installation.id, serverId)
                : NEXT_ID.incrementAndGet();
        showInternal(
                notificationId, category, title, message,
                installation.name == null ? "" : installation.name);
        if (serverId > 0) setServerCursor(installation.id, serverId);
    }

    private String[] localizedServerNotification(
            JSONObject notification, String category) {
        String code = normalizeToken(notification.optString("code", ""));
        JSONObject payload = notification.optJSONObject("data");
        JSONObject station = payload == null ? null : payload.optJSONObject("station");
        String stationName = station == null
                ? context.getString(R.string.station)
                : station.optString("name", context.getString(R.string.station));
        switch (code) {
            case "station_started":
                return new String[]{
                        context.getString(R.string.station_started_notification_title),
                        context.getString(
                                R.string.station_started_notification_message,
                                stationName)};
            case "station_stopped":
                return new String[]{
                        context.getString(R.string.station_stopped_notification_title),
                        context.getString(
                                R.string.station_stopped_notification_message,
                                stationName)};
            case "rain_active":
                return new String[]{
                        context.getString(R.string.rain_delay_started_notification_title),
                        context.getString(R.string.rain_active_notification_message)};
            default:
                if (CATEGORY_DIAGNOSTICS.equals(category)) {
                    return new String[]{
                            context.getString(R.string.diagnostic_notification_title),
                            context.getString(R.string.diagnostic_notification_message)};
                }
                if (CATEGORY_UPDATES.equals(category)) {
                    return new String[]{
                            context.getString(R.string.update_notification_title),
                            context.getString(R.string.update_notification_message)};
                }
                return new String[]{
                        context.getString(R.string.notification_received_title),
                        context.getString(R.string.notification_received_message)};
        }
    }

    /**
     * Consume the persistent /notifications list from a JobService. The first
     * background read only establishes a cursor, preventing historical events
     * from flooding the phone after an update or a fresh installation.
     */
    void processServerBatch(Installation installation, JSONArray items) {
        synchronized (SERVER_LOCK) {
            processServerBatchLocked(installation, items);
        }
    }

    private void processServerBatchLocked(
            Installation installation, JSONArray items) {
        if (installation == null || items == null) return;
        List<JSONObject> ordered = new ArrayList<>();
        long newest = 0L;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) continue;
            long id = item.optLong("id", 0L);
            if (id <= 0) continue;
            ordered.add(item);
            newest = Math.max(newest, id);
        }
        if (ordered.isEmpty()) return;

        long cursor = serverCursor(installation.id);
        if (newest < cursor) {
            // The server notification database was reset.
            replaceServerCursor(installation.id, newest);
            return;
        }

        ordered.sort(Comparator.comparingLong(value -> value.optLong("id", 0L)));
        if (cursor == 0L) {
            // A periodic job may first run several minutes after pairing. Do
            // not silently discard notifications that were created during
            // that gap, but still avoid flooding a newly paired phone with
            // old server history.
            for (JSONObject item : ordered) {
                if (isRecentlyCreated(item)) {
                    showServerNotificationLocked(installation, item);
                }
            }
            setServerCursor(installation.id, newest);
            return;
        }
        long processed = cursor;
        for (JSONObject item : ordered) {
            long id = item.optLong("id", 0L);
            if (id <= cursor) continue;
            showServerNotificationLocked(installation, item);
            processed = Math.max(processed, id);
        }
        if (processed > cursor) setServerCursor(installation.id, processed);
    }

    private static boolean isRecentlyCreated(JSONObject item) {
        try {
            long created = OffsetDateTime.parse(item.optString("created"))
                    .toInstant().toEpochMilli();
            long age = System.currentTimeMillis() - created;
            return age >= -60_000L && age <= INITIAL_RECENT_WINDOW_MS;
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean isEnabled() {
        return preferences.getBoolean("notifications_enabled", true);
    }

    void setEnabled(boolean enabled) {
        preferences.edit().putBoolean("notifications_enabled", enabled).apply();
        if (!enabled) manager.cancelAll();
        PushRegistrationManager.syncAll(context);
    }

    boolean isCategoryEnabled(String category) {
        return preferences.getBoolean("notification_" + normalize(category), true);
    }

    void setCategoryEnabled(String category, boolean enabled) {
        preferences.edit()
                .putBoolean("notification_" + normalize(category), enabled)
                .apply();
        PushRegistrationManager.syncAll(context);
    }

    void clearInstallation(String installationId) {
        preferences.edit().remove(cursorKey(installationId)).apply();
    }

    static String categoryForServerNotification(JSONObject data) {
        if (data == null) return CATEGORY_OTHER;
        String type = normalizeToken(data.optString(
                "type", data.optString("event_type", "")));
        String code = normalizeToken(data.optString("code", ""));
        String combined = type + " " + code;

        boolean stationType = containsAny(type, "station", "zone");
        if ((stationType && containsAny(
                code, "started", "start", "on", "activated", "running")) ||
                containsAny(combined,
                        "station_started", "station_start", "station_on",
                        "station_activated", "station_running")) {
            return CATEGORY_STATION_STARTED;
        }
        if ((stationType && containsAny(
                code, "stopped", "stop", "off", "deactivated", "finished")) ||
                containsAny(combined,
                        "station_stopped", "station_stop", "station_off",
                        "station_deactivated", "station_finished")) {
            return CATEGORY_STATION_STOPPED;
        }
        if (containsAny(type, "rain") || containsAny(
                code, "rain", "rain_delay", "rain_sensor") ||
                containsAny(combined, "rain_delay", "rain_sensor")) {
            return CATEGORY_RAIN;
        }
        if (containsAny(type, "diagnostic", "health", "incident") ||
                containsAny(code, "diagnostic", "health", "problem", "incident") ||
                containsAny(combined, "diagnostics", "diagnostic_problem")) {
            return CATEGORY_DIAGNOSTICS;
        }
        if (containsAny(type, "update", "upgrade", "rollback") ||
                containsAny(code, "update", "upgrade", "rollback", "revision")) {
            return CATEGORY_UPDATES;
        }
        return CATEGORY_OTHER;
    }

    private void showInternal(
            int id, String category, String title, String message, String source) {
        if (!isEnabled() || !isCategoryEnabled(category)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;

        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, id, launch, pendingFlags);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, CHANNEL)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true);
        if (source != null && !source.trim().isEmpty()) builder.setSubText(source.trim());
        manager.notify(id, builder.build());
    }

    private long serverCursor(String installationId) {
        return preferences.getLong(cursorKey(installationId), 0L);
    }

    private void setServerCursor(String installationId, long value) {
        long current = serverCursor(installationId);
        if (value < current) return;
        replaceServerCursor(installationId, value);
    }

    private void replaceServerCursor(String installationId, long value) {
        preferences.edit().putLong(cursorKey(installationId), value).apply();
    }

    private static int stableNotificationId(String installationId, long serverId) {
        int value = 17;
        value = 31 * value + (installationId == null ? 0 : installationId.hashCode());
        value = 31 * value + Long.hashCode(serverId);
        return value == Integer.MIN_VALUE ? 1 : Math.abs(value);
    }

    private static String cursorKey(String installationId) {
        return CURSOR_PREFIX + (installationId == null ? "" : installationId);
    }

    private static boolean recentServerCategory(String category) {
        Long when = RECENT_SERVER_CATEGORY.get(normalize(category));
        return when != null &&
                System.currentTimeMillis() - when < SERVER_FALLBACK_SUPPRESSION_MS;
    }

    private static void rememberServerCategory(String category) {
        RECENT_SERVER_CATEGORY.put(normalize(category), System.currentTimeMillis());
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_').replace('.', '_').replace('/', '_');
    }

    private static String normalize(String category) {
        return category == null || category.isEmpty() ? CATEGORY_OTHER : category;
    }
}
