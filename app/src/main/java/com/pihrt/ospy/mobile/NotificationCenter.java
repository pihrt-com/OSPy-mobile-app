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
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private final Context context;
    private final NotificationManager manager;
    private final SharedPreferences preferences;

    NotificationCenter(Context context) {
        this.context = context;
        manager = context.getSystemService(NotificationManager.class);
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(
                    context.getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);
        }
    }

    void show(String category, String title, String message) {
        show(NEXT_ID.incrementAndGet(), category, title, message);
    }

    void show(int id, String category, String title, String message) {
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
        android.app.Notification notification = builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(id, notification);
    }

    boolean isEnabled() {
        return preferences.getBoolean("notifications_enabled", true);
    }

    void setEnabled(boolean enabled) {
        preferences.edit().putBoolean("notifications_enabled", enabled).apply();
        if (!enabled) manager.cancelAll();
    }

    boolean isCategoryEnabled(String category) {
        return preferences.getBoolean("notification_" + normalize(category), true);
    }

    void setCategoryEnabled(String category, boolean enabled) {
        preferences.edit()
                .putBoolean("notification_" + normalize(category), enabled)
                .apply();
    }

    private static String normalize(String category) {
        return category == null || category.isEmpty() ? CATEGORY_OTHER : category;
    }
}
