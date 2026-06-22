package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ubtechinc.cruzr.assistant.sdk.AssistantManager;

/**
 * Keeps the Cruzr system voice assistant suppressed while Help Desk is in use.
 * Uses AssistantManager only — LeisureManager crashes this app on Sunny.
 */
public final class VoiceAssistantController {

    private static final String TAG = "VoiceAssistantController";
    private static final long KEEPALIVE_INTERVAL_MS = 1500L;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static boolean keepAliveActive;
    private static Runnable keepAliveRunnable;

    private VoiceAssistantController() {
    }

    public static void startKeepAliveSuppression(Context context) {
        Context appContext = context.getApplicationContext();
        keepAliveActive = true;
        suppressAssistantManager(appContext);
        scheduleKeepAlive(appContext);
    }

    public static void disableForHelpDesk(Context context) {
        suppressAssistantManager(context.getApplicationContext());
    }

    public static void stopKeepAliveSuppression() {
        keepAliveActive = false;
        if (keepAliveRunnable != null) {
            mainHandler.removeCallbacks(keepAliveRunnable);
            keepAliveRunnable = null;
        }
    }

    public static void restoreDefault(Context context) {
        stopKeepAliveSuppression();
        try {
            AssistantManager manager = AssistantManager.get(context.getApplicationContext());
            manager.switchAssistant(AssistantManager.TYPE_TURN_ON);
            manager.showOrHidePart(AssistantManager.TYPE_SHOW_PART_WAKEUP);
            manager.showOrHidePart(AssistantManager.TYPE_SHOW_PART_MESSAGE);
        } catch (Throwable error) {
            Log.w(TAG, "Could not restore Cruzr voice assistant", error);
        }
    }

    private static void scheduleKeepAlive(Context appContext) {
        if (keepAliveRunnable != null) {
            mainHandler.removeCallbacks(keepAliveRunnable);
        }
        keepAliveRunnable = () -> {
            if (!keepAliveActive) {
                return;
            }
            suppressAssistantManager(appContext);
            mainHandler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS);
        };
        mainHandler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS);
    }

    private static void suppressAssistantManager(Context context) {
        try {
            AssistantManager manager = AssistantManager.get(context);
            manager.switchAssistant(AssistantManager.TYPE_TURN_OFF);
            manager.hideAssistant();
            manager.showOrHidePart(AssistantManager.TYPE_HIDE_PART_WAKEUP);
            manager.showOrHidePart(AssistantManager.TYPE_HIDE_PART_MESSAGE);
        } catch (Throwable error) {
            Log.w(TAG, "Could not disable Cruzr voice assistant", error);
        }
    }
}
