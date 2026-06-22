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
 * Registers for Cruzr speech-resource competition, then starts listening optimistically.
 * Waiting for an explicit competition grant never completes on some Cruzr builds.
 */
public class SpeechResourceController implements CompetitionListener {

    public interface AccessCallback {
        void onGranted();

        void onDenied(String reason);
    }

    public interface AccessChangeListener {
        void onAccessLost();
    }

    private static final String TAG = "SpeechResourceController";
    /** Time to suppress the assistant before starting recognize(). */
    private static final long LISTEN_PREPARE_MS = 2000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;

    private CompetitionManager competitionManager;
    private CompetingItemGroup competingGroup;
    private AccessCallback pendingCallback;
    private AccessChangeListener accessChangeListener;
    private Runnable prepareRunnable;
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

    public void detach() {
        cancelAccessRequest();
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

    /**
     * Suppresses the assistant, registers for competition if needed, then starts listening.
     * Does not block on competition callbacks (they may never arrive on Sunny).
     */
    public void requestAccess(AccessCallback callback) {
        if (callback == null) {
            return;
        }
        cancelAccessRequest();
        pendingCallback = callback;

        VoiceAssistantController.prepareForListening(appContext);
        ensureAttached();

        if (tryGrantImmediately()) {
            return;
        }

        prepareRunnable = () -> {
            prepareRunnable = null;
            if (pendingCallback == null) {
                return;
            }
            acquired.set(true);
            deliverGranted();
        };
        mainHandler.postDelayed(prepareRunnable, LISTEN_PREPARE_MS);
    }

    public void cancelAccessRequest() {
        if (prepareRunnable != null) {
            mainHandler.removeCallbacks(prepareRunnable);
            prepareRunnable = null;
        }
        pendingCallback = null;
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

    private boolean tryGrantImmediately() {
        if (competitionManager == null || competingGroup == null) {
            return false;
        }
        try {
            if (competitionManager.isCompetingItemGroupReleased(competingGroup)) {
                acquired.set(true);
                deliverGranted();
                return true;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not check speech resource availability", error);
        }
        return false;
    }

    private void deliverGranted() {
        if (prepareRunnable != null) {
            mainHandler.removeCallbacks(prepareRunnable);
            prepareRunnable = null;
        }
        AccessCallback callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) {
            callback.onGranted();
        }
    }
}
