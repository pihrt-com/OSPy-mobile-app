package com.pihrt.ospy.mobile;

import android.app.Application;

import com.google.firebase.FirebaseApp;

public final class OSPyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        AppCheckProviderInstaller.install();
        NotificationScheduler.update(this, false);
        PushRegistrationManager.syncAll(this);
    }
}
