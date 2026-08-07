package com.pihrt.ospy.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class NotificationBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationScheduler.update(context, true);
    }
}
