package com.pihrt.ospy.mobile;

import android.app.job.JobParameters;
import android.app.job.JobService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NotificationPollingJobService extends JobService {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private ExecutorService executor;
    private volatile boolean stopped;

    @Override
    public boolean onStartJob(JobParameters params) {
        PushRegistrationManager.syncAll(this);
        if (!RUNNING.compareAndSet(false, true)) return false;
        stopped = false;
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                pollAllInstallations();
            } finally {
                RUNNING.set(false);
                if (!stopped) jobFinished(params, false);
                ExecutorService running = executor;
                if (running != null) running.shutdown();
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        ExecutorService running = executor;
        if (running != null) running.shutdownNow();
        return true;
    }

    private void pollAllInstallations() {
        NotificationCenter notifications = new NotificationCenter(this);
        if (!notifications.isEnabled()) return;

        InstallationStore store = new InstallationStore(this);
        final List<Installation> installations;
        try {
            installations = store.load();
        } catch (Exception ignored) {
            return;
        }

        for (Installation installation : installations) {
            if (stopped || !notifications.isEnabled()) return;
            try {
                ApiClient client = new ApiClient(installation, store);
                JSONObject response = client.requestBlocking(
                        "GET", "/notifications?limit=200", null);
                JSONArray items = response.optJSONArray("data");
                notifications.processServerBatch(installation, items);
            } catch (Exception ignored) {
                // One unreachable installation must not prevent checks of the
                // remaining saved systems. JobScheduler will run again later.
            }
        }
    }
}
