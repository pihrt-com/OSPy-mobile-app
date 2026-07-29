package com.pihrt.ospy.mobile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

final class NotificationCenter {
    private static final String CHANNEL = "ospy_events";
    private final Context context;
    private final NotificationManager manager;
    private final SharedPreferences preferences;

    NotificationCenter(Context context) {
        this.context = context;
        manager = context.getSystemService(NotificationManager.class);
        preferences = context.getSharedPreferences(
                "ospy_mobile_preferences", Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "OSPy events", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    void show(int id, String title, String message) {
        if (!isEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        android.app.Notification notification =
                new android.app.Notification.Builder(context, CHANNEL)
                        .setSmallIcon(com.pihrt.ospy.mobile.R.drawable.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
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
}
