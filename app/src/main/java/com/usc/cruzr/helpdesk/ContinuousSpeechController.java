package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.ubtrobot.async.CancelledCallback;
import com.ubtrobot.async.DoneCallback;
import com.ubtrobot.async.FailCallback;
import com.ubtrobot.async.ProgressCallback;
import com.ubtrobot.async.ProgressivePromise;
import com.ubtrobot.speech.RecognitionException;
import com.ubtrobot.speech.RecognitionOption;
import com.ubtrobot.speech.RecognitionProgress;
import com.ubtrobot.speech.RecognitionResult;
import com.ubtrobot.speech.SpeechManager;
import com.ubtrobot.speech.SynthesisException;
import com.ubtrobot.speech.SynthesisProgress;

/**
 * Auto-starts speech recognition and restarts after each answered question.
 */
public class ContinuousSpeechController {

    public interface Listener {
        void onStatusChanged(String status);

        void onPartialSpeech(String text);

        void onFinalSpeech(String text);

        void onSpeakingChanged(boolean speaking);

        void onError(String message);
    }

    private static final String TAG = "ContinuousSpeech";
    private static final long MIC_PREPARE_DELAY_MS = 300L;
    private static final long RECOGNITION_RETRY_DELAY_MS = 400L;
    private static final long POST_SPEECH_DELAY_MS = 800L;
    private static final long RECOGNITION_TIMEOUT_MS = 12000L;
    private static final int BARGE_IN_MIN_CHARS = 3;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;
    private final SpeechResourceController speechResources;

    private SpeechManager speechManager;
    private Listener listener;

    private ProgressivePromise<RecognitionResult, RecognitionException, RecognitionProgress> recognitionPromise;
    private ProgressivePromise<Void, SynthesisException, SynthesisProgress> synthesisPromise;

    private boolean continuousActive;
    private boolean speaking;
    private boolean recognitionStarting;
    private boolean awaitingResponse;
    private Runnable micPrepareRunnable;
    private Runnable recognitionRetryRunnable;
    private Runnable synthesisFallbackRunnable;

    public ContinuousSpeechController(Context context, SpeechResourceController speechResources) {
        appContext = context.getApplicationContext();
        this.speechResources = speechResources;
        this.speechResources.setAccessChangeListener(this::onMicAccessLost);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setDeniedMessage(String deniedMessage) {
        if (!TextUtils.isEmpty(deniedMessage)) {
            speechResources.setDeniedMessage(deniedMessage);
        }
    }

    public void bindSpeechManager(SpeechManager speechManager) {
        this.speechManager = speechManager;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public boolean isContinuousActive() {
        return continuousActive;
    }

    public void startContinuousListening() {
        if (speechManager == null || continuousActive) {
            return;
        }

        continuousActive = true;
        awaitingResponse = false;
        recognitionStarting = true;
        notifyStatus(Status.REQUESTING_MIC);

        try {
            VoiceAssistantController.disableForHelpDesk(appContext);
        } catch (Throwable ignored) {
        }

        speechResources.ensureAttached();
        cancelMicPrepare();
        micPrepareRunnable = this::requestMicrophoneAccess;
        mainHandler.postDelayed(micPrepareRunnable, MIC_PREPARE_DELAY_MS);
    }

    public void stopContinuousListening() {
        continuousActive = false;
        awaitingResponse = false;
        recognitionStarting = false;
        cancelMicPrepare();
        cancelRecognitionRetry();
        cancelSynthesisFallback();
        cancelRecognition();
        cancelSynthesis();
        speechResources.cancelAccessRequest();
        setSpeaking(false);
        try {
            if (speechManager != null) {
                speechManager.stopRecording();
            }
        } catch (Throwable ignored) {
        }
    }

    public void interruptCurrentSpeech() {
        cancelSynthesisFallback();
        cancelSynthesis();
        setSpeaking(false);
        awaitingResponse = false;
        cancelRecognition();
        if (continuousActive) {
            forceRestartListening(RECOGNITION_RETRY_DELAY_MS);
            notifyStatus(Status.INTERRUPTED);
        }
    }

    public void speak(String text) {
        cancelSynthesisFallback();

        if (speechManager == null || TextUtils.isEmpty(text)) {
            awaitingResponse = false;
            forceRestartListening(RECOGNITION_RETRY_DELAY_MS);
            return;
        }

        awaitingResponse = false;
        cancelRecognition();
        cancelSynthesis();
        setSpeaking(true);
        notifyStatus(Status.SPEAKING);

        try {
            synthesisPromise = speechManager.synthesize(text)
                    .done(new DoneCallback<Void>() {
                        @Override
                        public void onDone(Void result) {
                            mainHandler.post(() -> onSynthesisFinished());
                        }
                    })
                    .fail(new FailCallback<SynthesisException>() {
                        @Override
                        public void onFail(SynthesisException exception) {
                            mainHandler.post(() -> onSynthesisFinished());
                        }
                    })
                    .cancelled(new CancelledCallback() {
                        @Override
                        public void onCancelled() {
                            mainHandler.post(() -> onSynthesisFinished(true));
                        }
                    });
        } catch (Throwable error) {
            Log.w(TAG, "Could not synthesize response", error);
            onSynthesisFinished();
            return;
        }

        // Cruzr TTS done callbacks are unreliable; fall back to a duration estimate.
        synthesisFallbackRunnable = () -> {
            synthesisFallbackRunnable = null;
            if (speaking) {
                Log.w(TAG, "Synthesis callback missed; restarting listen via fallback timer");
                onSynthesisFinished(false);
            }
        };
        mainHandler.postDelayed(synthesisFallbackRunnable, estimateSpeechDurationMs(text));

        // Voice barge-in while the robot is speaking.
        scheduleRecognitionRestart(POST_SPEECH_DELAY_MS);
    }

    private void onMicAccessLost() {
        if (!continuousActive || speaking || awaitingResponse) {
            return;
        }
        Log.w(TAG, "Microphone released by system assistant; re-taking");
        VoiceAssistantController.disableForHelpDesk(appContext);
        cancelRecognition();
        forceRestartListening(800L);
    }

    private void requestMicrophoneAccess() {
        if (!continuousActive) {
            return;
        }

        speechResources.requestAccess(new SpeechResourceController.AccessCallback() {
            @Override
            public void onGranted() {
                mainHandler.post(() -> beginRecognition());
            }

            @Override
            public void onDenied(String reason) {
                mainHandler.post(() -> {
                    continuousActive = false;
                    recognitionStarting = false;
                    notifyError(reason);
                });
            }
        });
    }

    private void beginRecognition() {
        if (!continuousActive || speechManager == null || awaitingResponse) {
            recognitionStarting = false;
            return;
        }

        if (speaking) {
            recognitionStarting = false;
            return;
        }

        VoiceAssistantController.prepareForListening(appContext);

        try {
            speechManager.stopRecording();
        } catch (Throwable ignored) {
        }

        RecognitionOption option = new RecognitionOption.Builder(RecognitionOption.MODE_SINGLE)
                .setDistanceRange(RecognitionOption.DISTANCE_RANGE_NEAR_FIELD)
                .setTimeoutMillis(RECOGNITION_TIMEOUT_MS)
                .build();

        try {
            recognitionPromise = speechManager.recognize(option)
                    .progress(new ProgressCallback<RecognitionProgress>() {
                        @Override
                        public void onProgress(RecognitionProgress progress) {
                            mainHandler.post(() -> handleRecognitionProgress(progress));
                        }
                    })
                    .done(new DoneCallback<RecognitionResult>() {
                        @Override
                        public void onDone(RecognitionResult result) {
                            mainHandler.post(() -> handleRecognitionDone(result));
                        }
                    })
                    .fail(new FailCallback<RecognitionException>() {
                        @Override
                        public void onFail(RecognitionException exception) {
                            mainHandler.post(() -> handleRecognitionFailure(exception));
                        }
                    })
                    .cancelled(new CancelledCallback() {
                        @Override
                        public void onCancelled() {
                            mainHandler.post(() -> {
                                recognitionStarting = false;
                                recognitionPromise = null;
                                if (!speaking) {
                                    scheduleRecognitionRestart(RECOGNITION_RETRY_DELAY_MS);
                                }
                            });
                        }
                    });
            recognitionStarting = false;
            notifyStatus(Status.LISTENING);
        } catch (Throwable error) {
            Log.w(TAG, "Could not start recognition", error);
            recognitionStarting = false;
            VoiceAssistantController.prepareForListening(appContext);
            scheduleRecognitionRestart(1200L);
        }
    }

    private void handleRecognitionProgress(RecognitionProgress progress) {
        if (progress == null) {
            return;
        }

        String partial = progress.getTextResult();
        if (TextUtils.isEmpty(partial)) {
            return;
        }

        notifyPartialSpeech(partial);

        if (speaking && partial.trim().length() >= BARGE_IN_MIN_CHARS) {
            cancelSynthesisFallback();
            cancelSynthesis();
            setSpeaking(false);
            notifyStatus(Status.INTERRUPTED);
        }
    }

    private void handleRecognitionDone(RecognitionResult result) {
        recognitionPromise = null;
        recognitionStarting = false;

        String text = result != null ? result.getText() : null;
        if (TextUtils.isEmpty(text)) {
            if (!speaking) {
                scheduleRecognitionRestart(RECOGNITION_RETRY_DELAY_MS);
            }
            return;
        }

        if (speaking) {
            cancelSynthesisFallback();
            cancelSynthesis();
            setSpeaking(false);
        }

        awaitingResponse = true;
        try {
            notifyFinalSpeech(text);
        } finally {
            awaitingResponse = false;
        }
    }

    private void handleRecognitionFailure(RecognitionException exception) {
        recognitionPromise = null;
        recognitionStarting = false;

        String message = exception != null && !TextUtils.isEmpty(exception.getMessage())
                ? exception.getMessage()
                : "Speech recognition failed.";
        Log.w(TAG, message, exception);

        if (speaking) {
            return;
        }
        VoiceAssistantController.prepareForListening(appContext);
        if (!awaitingResponse) {
            scheduleRecognitionRestart(1200L);
        }
    }

    private void onSynthesisFinished() {
        onSynthesisFinished(false);
    }

    private void onSynthesisFinished(boolean cancelled) {
        cancelSynthesisFallback();
        synthesisPromise = null;
        setSpeaking(false);
        if (continuousActive) {
            forceRestartListening(cancelled ? RECOGNITION_RETRY_DELAY_MS : POST_SPEECH_DELAY_MS);
            if (!cancelled) {
                notifyStatus(Status.LISTENING);
            }
        }
    }

    /**
     * Tear down any stale recognizer session and start a fresh listen cycle.
     * Used after TTS so the second question is not blocked by a zombie session.
     */
    private void forceRestartListening(long delayMs) {
        if (!continuousActive) {
            return;
        }
        cancelRecognitionRetry();
        recognitionRetryRunnable = () -> {
            if (!continuousActive || awaitingResponse || speaking) {
                return;
            }
            cancelRecognition();
            recognitionStarting = true;
            beginRecognition();
        };
        mainHandler.postDelayed(recognitionRetryRunnable, delayMs);
    }

    private void scheduleRecognitionRestart(long delayMs) {
        if (!continuousActive || awaitingResponse) {
            return;
        }
        cancelRecognitionRetry();
        recognitionRetryRunnable = () -> {
            if (!continuousActive || awaitingResponse) {
                return;
            }
            if (speaking) {
                return;
            }
            if (speechManager != null && speechManager.isRecognizing()) {
                notifyStatus(Status.LISTENING);
                return;
            }
            recognitionStarting = true;
            beginRecognition();
        };
        mainHandler.postDelayed(recognitionRetryRunnable, delayMs);
    }

    private static long estimateSpeechDurationMs(String text) {
        if (TextUtils.isEmpty(text)) {
            return 2500L;
        }
        return Math.min(90000L, Math.max(3500L, text.length() * 90L + 2000L));
    }

    private void cancelRecognition() {
        if (recognitionPromise != null) {
            try {
                recognitionPromise.cancel();
            } catch (Throwable ignored) {
            }
            recognitionPromise = null;
        }
        try {
            if (speechManager != null) {
                speechManager.stopRecording();
            }
        } catch (Throwable ignored) {
        }
        recognitionStarting = false;
    }

    private void cancelSynthesis() {
        if (synthesisPromise != null) {
            try {
                synthesisPromise.cancel();
            } catch (Throwable ignored) {
            }
            synthesisPromise = null;
        }
    }

    private void cancelSynthesisFallback() {
        if (synthesisFallbackRunnable != null) {
            mainHandler.removeCallbacks(synthesisFallbackRunnable);
            synthesisFallbackRunnable = null;
        }
    }

    private void cancelMicPrepare() {
        if (micPrepareRunnable != null) {
            mainHandler.removeCallbacks(micPrepareRunnable);
            micPrepareRunnable = null;
        }
    }

    private void cancelRecognitionRetry() {
        if (recognitionRetryRunnable != null) {
            mainHandler.removeCallbacks(recognitionRetryRunnable);
            recognitionRetryRunnable = null;
        }
    }

    private void setSpeaking(boolean speaking) {
        if (this.speaking == speaking) {
            return;
        }
        this.speaking = speaking;
        if (listener != null) {
            listener.onSpeakingChanged(speaking);
        }
    }

    private void notifyStatus(String status) {
        if (listener != null) {
            listener.onStatusChanged(status);
        }
    }

    private void notifyPartialSpeech(String text) {
        if (listener != null) {
            listener.onPartialSpeech(text);
        }
    }

    private void notifyFinalSpeech(String text) {
        if (listener != null) {
            listener.onFinalSpeech(text);
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }

    static final class Status {
        static final String REQUESTING_MIC = "requesting_mic";
        static final String LISTENING = "listening";
        static final String SPEAKING = "speaking";
        static final String INTERRUPTED = "interrupted";

        private Status() {
        }
    }
}
