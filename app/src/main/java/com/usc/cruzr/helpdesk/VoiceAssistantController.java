package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.ubtechinc.cruzr.assistant.sdk.AssistantManager;
import com.ubtechinc.cruzr.sys.cruzrleisure.entity.Leisure;
import com.ubtechinc.cruzr.sys.cruzrleisure.leisure.LeisureManager;

import java.util.List;
import java.util.Locale;

/**
 * Keeps the Cruzr system voice assistant suppressed while Help Desk is in use.
 * The assistant can restart itself on the robot, so we re-apply suppression on a timer.
 */
public final class VoiceAssistantController {

    private static final String TAG = "VoiceAssistantController";
    private static final long KEEPALIVE_INTERVAL_MS = 2000L;
    private static final long LEISURE_RESCAN_INTERVAL_MS = 10000L;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static boolean keepAliveActive;
    private static boolean leisureInitialized;
    private static long lastLeisureScanMs;
    private static Runnable keepAliveRunnable;

    private VoiceAssistantController() {
    }

    public static void startKeepAliveSuppression(Context context) {
        Context appContext = context.getApplicationContext();
        keepAliveActive = true;
        suppressNow(appContext, true);
        scheduleKeepAlive(appContext);
    }

    public static void disableForHelpDesk(Context context) {
        suppressNow(context.getApplicationContext(), true);
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
            if (leisureInitialized) {
                LeisureManager.get().unProhibitedLeisure();
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not restore Cruzr leisure behaviours", error);
        }

        leisureInitialized = false;
        lastLeisureScanMs = 0L;
    }

    private static void scheduleKeepAlive(Context appContext) {
        if (keepAliveRunnable != null) {
            mainHandler.removeCallbacks(keepAliveRunnable);
        }
        keepAliveRunnable = () -> {
            if (!keepAliveActive) {
                return;
            }
            suppressNow(appContext, false);
            mainHandler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS);
        };
        mainHandler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS);
    }

    private static void suppressNow(Context appContext, boolean forceLeisureScan) {
        suppressAssistantManager(appContext);
        suppressLeisure(appContext, forceLeisureScan);
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

    private static void suppressLeisure(Context context, boolean forceLeisureScan) {
        try {
            LeisureManager leisureManager = LeisureManager.get();
            if (!leisureInitialized) {
                leisureManager.init(context);
                leisureInitialized = true;
            }
            leisureManager.prohibitedLeisure();

            long now = System.currentTimeMillis();
            if (forceLeisureScan || now - lastLeisureScanMs >= LEISURE_RESCAN_INTERVAL_MS) {
                disableVoiceLeisures(leisureManager);
                lastLeisureScanMs = now;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not prohibit Cruzr leisure behaviours", error);
            leisureInitialized = false;
        }
    }

    private static void disableVoiceLeisures(LeisureManager leisureManager) {
        List<Leisure> leisures;
        try {
            leisures = leisureManager.getLeisures();
        } catch (Throwable error) {
            Log.w(TAG, "Could not list Cruzr leisure entries", error);
            return;
        }
        if (leisures == null || leisures.isEmpty()) {
            return;
        }

        for (Leisure leisure : leisures) {
            if (leisure == null || !isVoiceRelatedLeisure(leisure)) {
                continue;
            }
            String key = leisure.getKey();
            if (TextUtils.isEmpty(key)) {
                continue;
            }
            try {
                leisureManager.enableLeisure(key, false);
            } catch (Throwable error) {
                Log.w(TAG, "Could not disable leisure: " + key, error);
            }
        }
    }

    private static boolean isVoiceRelatedLeisure(Leisure leisure) {
        if (leisure.isWakeup()) {
            return true;
        }
        String combined = String.format(
                Locale.US,
                "%s %s %s %s",
                safeLower(leisure.getKey()),
                safeLower(leisure.getSkillName()),
                safeLower(leisure.getPackageName()),
                safeLower(leisure.getAction())
        );
        return combined.contains("assistant")
                || combined.contains("voice")
                || combined.contains("wakeup")
                || combined.contains("speech")
                || combined.contains("dialog")
                || combined.contains("llm");
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
