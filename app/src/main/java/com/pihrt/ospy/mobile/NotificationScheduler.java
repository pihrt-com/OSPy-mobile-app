package com.pihrt.ospy.mobile;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class NotificationScheduler {
    private static final int PERIODIC_JOB_ID = 0x4F535011;
    private static final int IMMEDIATE_JOB_ID = 0x4F535012;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    private NotificationScheduler() {
    }

    static void update(Context context, boolean runNow) {
        Context app = context.getApplicationContext();
        JobScheduler scheduler = app.getSystemService(JobScheduler.class);
        if (scheduler == null) return;

        NotificationCenter notifications = new NotificationCenter(app);
        if (!notifications.isEnabled()) {
            scheduler.cancel(PERIODIC_JOB_ID);
            scheduler.cancel(IMMEDIATE_JOB_ID);
            return;
        }

        ComponentName service = new ComponentName(
                app, NotificationPollingJobService.class);
        JobInfo periodic = new JobInfo.Builder(PERIODIC_JOB_ID, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build();
        scheduler.schedule(periodic);

        if (runNow) {
            JobInfo immediate = new JobInfo.Builder(IMMEDIATE_JOB_ID, service)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(1_000L)
                    .setOverrideDeadline(30_000L)
                    .build();
            scheduler.schedule(immediate);
        }
    }
}
