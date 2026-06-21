package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.util.Log;

import com.ubtrobot.Robot;
import com.ubtrobot.speech.SpeechManager;

/**
 * Safely initializes Cruzr SDK services without crashing the app.
 */
public final class RobotBootstrap {

    private static final String TAG = "RobotBootstrap";

    private static volatile boolean robotInitialized;

    private RobotBootstrap() {
    }

    public static boolean ensureInitialized(Context context) {
        if (robotInitialized) {
            return true;
        }

        try {
            Robot.initialize(context.getApplicationContext());
            robotInitialized = true;
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Robot.initialize failed", error);
            return false;
        }
    }

    public static SpeechManager getSpeechManager() {
        if (!robotInitialized) {
            return null;
        }

        try {
            return Robot.globalContext().getSystemService(SpeechManager.SERVICE);
        } catch (Throwable error) {
            Log.w(TAG, "SpeechManager unavailable", error);
            return null;
        }
    }
}
