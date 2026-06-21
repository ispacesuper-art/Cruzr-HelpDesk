package com.usc.cruzr.helpdesk;

import android.app.Application;

public class CruzrApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RobotBootstrap.ensureInitialized(this);
    }
}
