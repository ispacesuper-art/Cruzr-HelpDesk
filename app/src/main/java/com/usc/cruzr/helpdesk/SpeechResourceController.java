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

        void onRetrying(int attempt, int maxAttempts);
    }

    public interface AccessChangeListener {
        void onAccessLost();
    }

    private static final String TAG = "SpeechResourceController";
    private static final long ACCESS_ATTEMPT_TIMEOUT_MS = 5000L;
    private static final long RETRY_DELAY_MS = 1500L;
    private static final int MAX_ACCESS_ATTEMPTS = 10;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;

    private CompetitionManager competitionManager;
    private CompetingItemGroup competingGroup;
    private AccessCallback pendingCallback;
    private AccessChangeListener accessChangeListener;
    private Runnable timeoutRunnable;
    private String deniedMessage = "The Cruzr voice assistant is still using the microphone.";
    private int accessAttempt;
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

    public void setAccessChangeListener(AccessChangeListener listener) {
        this.accessChangeListener = listener;
    }

    public void ensureAttached() {
        if (!attached.get()) {
            attach();
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
        accessAttempt = 0;
        pendingCallback = callback;
        beginAccessAttempt();
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
        boolean wasAcquired = acquired.getAndSet(false);
        if (wasAcquired && accessChangeListener != null) {
            mainHandler.post(accessChangeListener::onAccessLost);
        }
    }

    private void beginAccessAttempt() {
        VoiceAssistantController.disableForHelpDesk(appContext);

        if (acquired.get()) {
            deliverGranted();
            return;
        }

        ensureAttached();
        tryGrantIfResourcesReleased();

        cancelTimeout();
        timeoutRunnable = () -> {
            if (acquired.get()) {
                deliverGranted();
                return;
            }
            if (accessAttempt >= MAX_ACCESS_ATTEMPTS) {
                AccessCallback callback = pendingCallback;
                pendingCallback = null;
                if (callback != null) {
                    callback.onDenied(deniedMessage);
                }
                return;
            }
            accessAttempt++;
            AccessCallback callback = pendingCallback;
            if (callback != null) {
                callback.onRetrying(accessAttempt, MAX_ACCESS_ATTEMPTS);
            }
            reacquire();
            mainHandler.postDelayed(this::beginAccessAttempt, RETRY_DELAY_MS);
        };
        mainHandler.postDelayed(timeoutRunnable, ACCESS_ATTEMPT_TIMEOUT_MS);
    }

    private void tryGrantIfResourcesReleased() {
        if (competitionManager == null || competingGroup == null) {
            return;
        }
        try {
            if (competitionManager.isCompetingItemGroupReleased(competingGroup)) {
                acquired.set(true);
                deliverGranted();
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not check speech resource availability", error);
        }
    }

    private void deliverGranted() {
        cancelTimeout();
        AccessCallback callback = pendingCallback;
        pendingCallback = null;
        accessAttempt = 0;
        if (callback != null) {
            callback.onGranted();
        }
    }

    private void cancelPendingRequest() {
        cancelTimeout();
        pendingCallback = null;
        accessAttempt = 0;
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }
}
