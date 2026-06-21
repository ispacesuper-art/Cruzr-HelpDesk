package com.usc.cruzr.helpdesk;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.ubtrobot.async.DoneCallback;
import com.ubtrobot.async.FailCallback;
import com.ubtrobot.async.ProgressCallback;
import com.ubtrobot.speech.RecognitionException;
import com.ubtrobot.speech.RecognitionOption;
import com.ubtrobot.speech.RecognitionProgress;
import com.ubtrobot.speech.RecognitionResult;
import com.ubtrobot.speech.SpeechManager;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final long MIC_PREPARE_DELAY_MS = 1000L;
    private static final long ROBOT_INIT_DELAY_MS = 250L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private TextView userText;
    private TextView responseText;
    private MaterialButton listenButton;
    private MaterialButton stopButton;
    private MaterialButton welcomeButton;
    private LinearLayout topicButtonContainer;

    private HelpDeskEngine helpDeskEngine;
    private SpeechManager speechManager;
    private SpeechResourceController speechResources;
    private boolean isListening;
    private boolean robotReady;
    private Runnable micPrepareRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        userText = findViewById(R.id.userText);
        responseText = findViewById(R.id.responseText);
        listenButton = findViewById(R.id.listenButton);
        stopButton = findViewById(R.id.stopButton);
        welcomeButton = findViewById(R.id.welcomeButton);
        topicButtonContainer = findViewById(R.id.topicButtonContainer);

        helpDeskEngine = new HelpDeskEngine(this);
        speechResources = new SpeechResourceController(this);
        speechResources.setDeniedMessage(getString(R.string.error_mic_busy));

        listenButton.setOnClickListener(v -> startListening());
        stopButton.setOnClickListener(v -> stopListening());
        welcomeButton.setOnClickListener(v -> deliverResponse(helpDeskEngine.getWelcomeResponse(), null));

        buildTopicButtons();
        ensureMicrophonePermission();
        statusText.setText(R.string.status_starting);

        mainHandler.postDelayed(this::initializeRobotServices, ROBOT_INIT_DELAY_MS);
    }

    private void initializeRobotServices() {
        robotReady = RobotBootstrap.ensureInitialized(this);
        if (!robotReady) {
            statusText.setText(R.string.status_robot_unavailable);
            listenButton.setEnabled(false);
            return;
        }

        speechManager = RobotBootstrap.getSpeechManager();
        if (speechManager == null) {
            statusText.setText(R.string.status_speech_unavailable);
            listenButton.setEnabled(false);
            return;
        }

        try {
            VoiceAssistantController.disableForHelpDesk(this);
        } catch (Throwable ignored) {
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            setReadyState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!robotReady) {
            mainHandler.postDelayed(this::initializeRobotServices, ROBOT_INIT_DELAY_MS);
            return;
        }
        if (speechManager != null) {
            try {
                VoiceAssistantController.disableForHelpDesk(this);
            } catch (Throwable ignored) {
            }
            speechResources.attach();
        }
    }

    @Override
    protected void onPause() {
        stopListening();
        super.onPause();
    }

    private void buildTopicButtons() {
        topicButtonContainer.removeAllViews();
        int margin = dpToPx(8);

        for (HelpDeskEngine.Topic topic : helpDeskEngine.getTopics()) {
            AppCompatButton button = new AppCompatButton(this);
            button.setText(topic.label);
            button.setAllCaps(false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = margin;
            button.setLayoutParams(params);
            button.setOnClickListener(v -> handleTopicSelection(topic.id));
            topicButtonContainer.addView(button);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    private void ensureMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (speechManager != null) {
                setReadyState();
            }
        } else {
            statusText.setText(R.string.status_no_permission);
            listenButton.setEnabled(false);
            Toast.makeText(this, R.string.toast_allow_microphone, Toast.LENGTH_LONG).show();
        }
    }

    private void setReadyState() {
        statusText.setText(R.string.status_ready);
        listenButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void startListening() {
        if (isListening || speechManager == null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ensureMicrophonePermission();
            return;
        }

        try {
            VoiceAssistantController.disableForHelpDesk(this);
        } catch (Throwable ignored) {
        }
        speechResources.reacquire();

        userText.setText(R.string.label_listening);
        responseText.setText("");
        statusText.setText(R.string.status_pausing_assistant);
        listenButton.setEnabled(false);
        stopButton.setEnabled(true);

        micPrepareRunnable = this::requestMicrophoneAccess;
        mainHandler.postDelayed(micPrepareRunnable, MIC_PREPARE_DELAY_MS);
    }

    private void requestMicrophoneAccess() {
        if (isFinishing()) {
            return;
        }

        statusText.setText(R.string.status_requesting_mic);
        speechResources.requestAccess(new SpeechResourceController.AccessCallback() {
            @Override
            public void onGranted() {
                runOnUiThread(() -> beginRecognition());
            }

            @Override
            public void onDenied(String reason) {
                runOnUiThread(() -> {
                    listenButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    showError(reason);
                });
            }
        });
    }

    private void beginRecognition() {
        if (speechManager == null || isListening) {
            listenButton.setEnabled(true);
            stopButton.setEnabled(false);
            return;
        }

        try {
            VoiceAssistantController.disableForHelpDesk(this);
        } catch (Throwable ignored) {
        }

        try {
            speechManager.stopRecording();
        } catch (Throwable ignored) {
        }

        isListening = true;
        statusText.setText(R.string.status_listening);

        RecognitionOption option = new RecognitionOption.Builder(RecognitionOption.MODE_SINGLE)
                .setDistanceRange(RecognitionOption.DISTANCE_RANGE_NEAR_FIELD)
                .setTimeoutMillis(12000)
                .build();

        try {
            speechManager.recognize(option)
                    .progress(new ProgressCallback<RecognitionProgress>() {
                        @Override
                        public void onProgress(RecognitionProgress progress) {
                            runOnUiThread(() -> {
                                String partial = progress.getTextResult();
                                if (!TextUtils.isEmpty(partial)) {
                                    userText.setText(partial);
                                }
                            });
                        }
                    })
                    .done(new DoneCallback<RecognitionResult>() {
                        @Override
                        public void onDone(RecognitionResult result) {
                            runOnUiThread(() -> {
                                finishListeningUi();
                                handleUserInput(result != null ? result.getText() : "");
                            });
                        }
                    })
                    .fail(new FailCallback<RecognitionException>() {
                        @Override
                        public void onFail(RecognitionException exception) {
                            runOnUiThread(() -> {
                                finishListeningUi();
                                String message = exception != null && !TextUtils.isEmpty(exception.getMessage())
                                        ? exception.getMessage()
                                        : getString(R.string.error_recognition_failed);
                                showError(message);
                            });
                        }
                    });
        } catch (Throwable error) {
            finishListeningUi();
            showError(getString(R.string.error_recognition_failed));
        }
    }

    private void finishListeningUi() {
        isListening = false;
        listenButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void stopListening() {
        if (micPrepareRunnable != null) {
            mainHandler.removeCallbacks(micPrepareRunnable);
            micPrepareRunnable = null;
        }
        if (speechManager != null) {
            try {
                speechManager.stopRecording();
            } catch (Throwable ignored) {
            }
        }
        isListening = false;
        listenButton.setEnabled(true);
        stopButton.setEnabled(false);
        if (speechManager != null) {
            statusText.setText(R.string.status_ready);
        }
    }

    private void handleTopicSelection(String topicId) {
        HelpDeskEngine.MatchResult match = helpDeskEngine.matchTopicId(topicId);
        userText.setText(match.topic != null ? match.topic.label : "");
        deliverResponse(match.response, match.topic);
    }

    private void handleUserInput(String spokenText) {
        if (TextUtils.isEmpty(spokenText)) {
            showError(getString(R.string.error_no_speech));
            return;
        }

        userText.setText(spokenText);
        HelpDeskEngine.MatchResult match = helpDeskEngine.match(spokenText);
        deliverResponse(match.response, match.topic);
    }

    private void deliverResponse(String response, HelpDeskEngine.Topic topic) {
        responseText.setText(response);
        statusText.setText(topic != null
                ? getString(R.string.status_answered_topic, topic.label)
                : getString(R.string.status_answered));

        if (speechManager != null) {
            try {
                speechManager.synthesize(response);
            } catch (Throwable ignored) {
            }
        }
    }

    private void showError(String message) {
        statusText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        stopListening();
        speechResources.detach();
        try {
            VoiceAssistantController.restoreDefault(this);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
