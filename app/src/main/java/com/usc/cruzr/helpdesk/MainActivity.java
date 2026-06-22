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
import com.ubtrobot.speech.SpeechManager;

public class MainActivity extends AppCompatActivity implements ContinuousSpeechController.Listener {

    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final long ROBOT_INIT_DELAY_MS = 250L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private TextView userText;
    private TextView responseText;
    private MaterialButton listenButton;
    private MaterialButton stopButton;
    private MaterialButton exitButton;
    private LinearLayout topicButtonContainer;

    private HelpDeskEngine helpDeskEngine;
    private SpeechManager speechManager;
    private SpeechResourceController speechResources;
    private ContinuousSpeechController speechController;
    private boolean robotReady;
    private boolean speaking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        userText = findViewById(R.id.userText);
        responseText = findViewById(R.id.responseText);
        listenButton = findViewById(R.id.listenButton);
        stopButton = findViewById(R.id.stopButton);
        exitButton = findViewById(R.id.exitButton);
        topicButtonContainer = findViewById(R.id.topicButtonContainer);

        helpDeskEngine = new HelpDeskEngine(this);
        speechResources = new SpeechResourceController(this);
        speechResources.setDeniedMessage(getString(R.string.error_mic_busy));

        speechController = new ContinuousSpeechController(this, speechResources);
        speechController.setDeniedMessage(getString(R.string.error_mic_busy));
        speechController.setListener(this);

        listenButton.setOnClickListener(v -> interruptCurrentSpeech());
        stopButton.setOnClickListener(v -> pauseListening());
        exitButton.setOnClickListener(v -> exitApp());

        buildQuickAskButtons();
        ensureMicrophonePermission();
        updateListenButtonLabel();
        statusText.setText(R.string.status_starting);

        mainHandler.postDelayed(this::initializeRobotServices, ROBOT_INIT_DELAY_MS);
    }

    private void initializeRobotServices() {
        try {
            robotReady = RobotBootstrap.ensureInitialized(this);
            if (!robotReady) {
                statusText.setText(R.string.status_robot_unavailable);
                listenButton.setEnabled(false);
                stopButton.setEnabled(false);
                return;
            }

            speechManager = RobotBootstrap.getSpeechManager();
            if (speechManager == null) {
                statusText.setText(R.string.status_speech_unavailable);
                listenButton.setEnabled(false);
                stopButton.setEnabled(false);
                return;
            }

            speechController.bindSpeechManager(speechManager);
            VoiceAssistantController.startKeepAliveSuppression(this);
            speechResources.attach();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startAutomaticListening();
            }
        } catch (Throwable error) {
            statusText.setText(R.string.status_robot_unavailable);
            listenButton.setEnabled(false);
            stopButton.setEnabled(false);
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
            VoiceAssistantController.startKeepAliveSuppression(this);
            speechResources.attach();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                    && !speechController.isContinuousActive()) {
                startAutomaticListening();
            }
        }
    }

    @Override
    protected void onPause() {
        speechController.stopContinuousListening();
        super.onPause();
    }

    private void startAutomaticListening() {
        listenButton.setEnabled(true);
        stopButton.setEnabled(true);
        userText.setText(R.string.label_waiting);
        speechController.startContinuousListening();
    }

    private void pauseListening() {
        speechController.stopContinuousListening();
        statusText.setText(R.string.status_paused);
        userText.setText(R.string.label_paused);
    }

    private void interruptCurrentSpeech() {
        if (speaking) {
            speechController.interruptCurrentSpeech();
            statusText.setText(R.string.status_interrupted);
            return;
        }
        if (!speechController.isContinuousActive()) {
            startAutomaticListening();
        }
    }

    private void exitApp() {
        speechController.stopContinuousListening();
        try {
            VoiceAssistantController.restoreDefault(this);
        } catch (Throwable ignored) {
        }
        finish();
    }

    private void buildQuickAskButtons() {
        topicButtonContainer.removeAllViews();
        int margin = dpToPx(8);
        int horizontalPadding = dpToPx(20);
        int verticalPadding = dpToPx(12);

        addQuickAskButton(R.string.quick_ask_wifi, "wifi", margin, horizontalPadding, verticalPadding);
        addQuickAskButton(R.string.quick_ask_reception, "location", margin, horizontalPadding, verticalPadding);
        addQuickAskButton(R.string.quick_ask_amenities, "food", margin, horizontalPadding, verticalPadding);
        addQuickAskButton(R.string.quick_ask_parking, "parking", margin, horizontalPadding, verticalPadding);
    }

    private void addQuickAskButton(int labelResId, String topicId, int margin,
                                   int horizontalPadding, int verticalPadding) {
        AppCompatButton button = new AppCompatButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(labelResId);
        button.setAllCaps(false);
        button.setTextColor(ContextCompat.getColor(this, R.color.unisc_text));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setBackgroundResource(R.drawable.bg_button_pill_quick_ask);
        button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        button.setMinHeight(dpToPx(48));
        button.setMinWidth(0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(margin);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> handleTopicSelection(topicId));
        topicButtonContainer.addView(button);
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
                startAutomaticListening();
            }
        } else {
            statusText.setText(R.string.status_no_permission);
            listenButton.setEnabled(false);
            stopButton.setEnabled(false);
            Toast.makeText(this, R.string.toast_allow_microphone, Toast.LENGTH_LONG).show();
        }
    }

    private void handleTopicSelection(String topicId) {
        speechController.interruptCurrentSpeech();
        HelpDeskEngine.MatchResult match = helpDeskEngine.matchTopicId(topicId);
        userText.setText(match.topic != null ? match.topic.label : "");
        deliverResponse(match.response, match.topic);
    }

    private void deliverResponse(String response, HelpDeskEngine.Topic topic) {
        responseText.setText(response);
        statusText.setText(topic != null
                ? getString(R.string.status_answered_topic, topic.label)
                : getString(R.string.status_answered));
        speechController.speak(response);
    }

    private void updateListenButtonLabel() {
        listenButton.setText(speaking ? R.string.btn_interrupt : R.string.btn_listen);
    }

    private void showError(String message) {
        statusText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onStatusChanged(String status) {
        switch (status) {
            case ContinuousSpeechController.Status.REQUESTING_MIC:
                statusText.setText(R.string.status_requesting_mic);
                break;
            case ContinuousSpeechController.Status.LISTENING:
                statusText.setText(R.string.status_listening_always);
                break;
            case ContinuousSpeechController.Status.SPEAKING:
                statusText.setText(R.string.status_speaking);
                break;
            case ContinuousSpeechController.Status.INTERRUPTED:
                statusText.setText(R.string.status_interrupted);
                break;
            default:
                break;
        }
    }

    @Override
    public void onPartialSpeech(String text) {
        userText.setText(text);
    }

    @Override
    public void onFinalSpeech(String text) {
        if (TextUtils.isEmpty(text)) {
            showError(getString(R.string.error_no_speech));
            return;
        }
        userText.setText(text);
        HelpDeskEngine.MatchResult match = helpDeskEngine.match(text);
        deliverResponse(match.response, match.topic);
    }

    @Override
    public void onSpeakingChanged(boolean speaking) {
        this.speaking = speaking;
        updateListenButtonLabel();
    }

    @Override
    public void onError(String message) {
        showError(message);
    }

    @Override
    protected void onDestroy() {
        speechController.stopContinuousListening();
        speechResources.detach();
        try {
            VoiceAssistantController.restoreDefault(this);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
