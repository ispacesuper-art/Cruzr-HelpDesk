package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ubtrobot.Robot;
import com.ubtrobot.competition.CompetingItem;
import com.ubtrobot.competition.CompetingItemGroup;
import com.ubtrobot.competition.CompetitionListener;
import com.ubtrobot.competition.CompetitionManager;
import com.ubtrobot.speech.SpeechConstants;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Requests exclusive access to Cruzr speech recognition and synthesis.
 */
public class SpeechResourceController implements CompetitionListener {

    public interface AccessCallback {
        void onGranted();

        void onDenied(String reason);
    }

    private static final String TAG = "SpeechResourceController";
    private static final long ACCESS_TIMEOUT_MS = 15000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;

    private CompetitionManager competitionManager;
    private CompetingItemGroup competingGroup;
    private AccessCallback pendingCallback;
    private Runnable timeoutRunnable;
    private String deniedMessage = "The Cruzr voice assistant is still using the microphone.";
    private final AtomicBoolean acquired = new AtomicBoolean(false);
    private final AtomicBoolean attached = new AtomicBoolean(false);

    public SpeechResourceController(Context context) {
        appContext = context.getApplicationContext();
    }

    public void setDeniedMessage(String deniedMessage) {
        if (deniedMessage != null && !deniedMessage.isEmpty()) {
            this.deniedMessage = deniedMessage;
        }
    }

    public void attach() {
        if (attached.getAndSet(true)) {
            return;
        }

        try {
            competitionManager = Robot.globalContext().getSystemService(CompetitionManager.SERVICE);
            competingGroup = new CompetingItemGroup(Arrays.asList(
                    new CompetingItem(
                            SpeechConstants.SERVICE_RECOGNITION,
                            SpeechConstants.COMPETING_ITEM_RECOGNIZER
                    ),
                    new CompetingItem(
                            SpeechConstants.SERVICE_SYNTHESIS,
                            SpeechConstants.COMPETING_ITEM_SYNTHESIZER
                    )
            ));
            competitionManager.registerCompetitionListener(this, competingGroup);
        } catch (Throwable error) {
            attached.set(false);
            Log.w(TAG, "Could not register for speech resources", error);
        }
    }

    public void reacquire() {
        detach();
        attach();
    }

    public void detach() {
        cancelPendingRequest();
        if (!attached.getAndSet(false)) {
            return;
        }

        try {
            if (competitionManager != null) {
                competitionManager.unregisterCompetitionListener(this);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not unregister speech resources", error);
        }

        acquired.set(false);
        competingGroup = null;
        competitionManager = null;
    }

    public void requestAccess(AccessCallback callback) {
        if (callback == null) {
            return;
        }

        if (acquired.get()) {
            callback.onGranted();
            return;
        }

        if (!attached.get()) {
            attach();
        }

        pendingCallback = callback;
        cancelTimeout();
        timeoutRunnable = () -> {
            AccessCallback timedOut = pendingCallback;
            pendingCallback = null;
            if (timedOut != null) {
                timedOut.onDenied(deniedMessage);
            }
        };
        mainHandler.postDelayed(timeoutRunnable, ACCESS_TIMEOUT_MS);
    }

    public boolean hasAccess() {
        return acquired.get();
    }

    @Override
    public void onCompetingItemGroupAcquired(CompetingItemGroup group) {
        if (competingGroup == null || !competingGroup.getId().equals(group.getId())) {
            return;
        }
        acquired.set(true);
        mainHandler.post(this::deliverGranted);
    }

    @Override
    public void onCompetingItemGroupReleased(CompetingItemGroup group) {
        if (competingGroup == null || !competingGroup.getId().equals(group.getId())) {
            return;
        }
        acquired.set(false);
    }

    private void deliverGranted() {
        cancelTimeout();
        AccessCallback callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) {
            callback.onGranted();
        }
    }

    private void cancelPendingRequest() {
        cancelTimeout();
        pendingCallback = null;
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }
}

