package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ubtechinc.cruzr.assistant.sdk.AssistantManager;
import com.ubtechinc.cruzr.sys.cruzrleisure.callback.IinitListener;
import com.ubtechinc.cruzr.sys.cruzrleisure.leisure.LeisureManager;

/**
 * Keeps the Cruzr system voice assistant suppressed while Help Desk is in use.
 */
public final class VoiceAssistantController {

    private static final String TAG = "VoiceAssistantController";
    private static final long KEEPALIVE_INTERVAL_MS = 1500L;
    private static final long LEISURE_START_DELAY_MS = 5000L;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static boolean keepAliveActive;
    private static boolean leisureReady;
    private static boolean leisureInitStarted;
    private static boolean leisureInitFailed;
    private static Runnable keepAliveRunnable;
    private static Runnable leisureStartRunnable;

    private VoiceAssistantController() {
    }

    public static void startKeepAliveSuppression(Context context) {
        Context appContext = context.getApplicationContext();
        keepAliveActive = true;
        suppressAssistantManager(appContext);
        scheduleKeepAlive(appContext);
        scheduleDeferredLeisureSuppression(appContext);
    }

    public static void disableForHelpDesk(Context context) {
        suppressAssistantManager(context.getApplicationContext());
        applyLeisureSuppressionIfReady();
    }

    public static void stopKeepAliveSuppression() {
        keepAliveActive = false;
        if (keepAliveRunnable != null) {
            mainHandler.removeCallbacks(keepAliveRunnable);
            keepAliveRunnable = null;
        }
        if (leisureStartRunnable != null) {
            mainHandler.removeCallbacks(leisureStartRunnable);
            leisureStartRunnable = null;
        }
    }

    public static void restoreDefault(Context context) {
        stopKeepAliveSuppression();
        Context appContext = context.getApplicationContext();

        try {
            AssistantManager manager = AssistantManager.get(appContext);
            manager.switchAssistant(AssistantManager.TYPE_TURN_ON);
            manager.showOrHidePart(AssistantManager.TYPE_SHOW_PART_WAKEUP);
            manager.showOrHidePart(AssistantManager.TYPE_SHOW_PART_MESSAGE);
        } catch (Throwable error) {
            Log.w(TAG, "Could not restore Cruzr voice assistant", error);
        }

        try {
            if (leisureReady) {
                LeisureManager.get().unProhibitedLeisure();
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not restore Cruzr leisure behaviours", error);
        }

        leisureReady = false;
        leisureInitStarted = false;
        leisureInitFailed = false;
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
            applyLeisureSuppressionIfReady();
            mainHandler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS);
        };
        mainHandler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS);
    }

    private static void scheduleDeferredLeisureSuppression(Context appContext) {
        if (leisureInitFailed || leisureReady || leisureInitStarted) {
            return;
        }
        if (leisureStartRunnable != null) {
            mainHandler.removeCallbacks(leisureStartRunnable);
        }
        leisureStartRunnable = () -> {
            leisureStartRunnable = null;
            if (!keepAliveActive || leisureInitFailed || leisureReady) {
                return;
            }
            startLeisureSuppression(appContext);
        };
        mainHandler.postDelayed(leisureStartRunnable, LEISURE_START_DELAY_MS);
    }

    private static void startLeisureSuppression(Context appContext) {
        if (leisureInitFailed || leisureReady || leisureInitStarted) {
            return;
        }
        leisureInitStarted = true;
        try {
            LeisureManager leisureManager = LeisureManager.get();
            leisureManager.init(appContext, new IinitListener() {
                @Override
                public void onInit() {
                    mainHandler.post(() -> {
                        leisureReady = true;
                        applyLeisureSuppressionIfReady();
                        Log.i(TAG, "Leisure suppression connected");
                    });
                }
            });
        } catch (Throwable error) {
            leisureInitStarted = false;
            leisureInitFailed = true;
            Log.w(TAG, "Leisure suppression unavailable on this robot", error);
        }
    }

    private static void applyLeisureSuppressionIfReady() {
        if (!keepAliveActive || !leisureReady) {
            return;
        }
        try {
            LeisureManager.get().prohibitedLeisure();
        } catch (Throwable error) {
            Log.w(TAG, "Could not apply leisure suppression", error);
        }
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
