package com.example.vocalharmony.ui.home; // Ensure correct package

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.example.vocalharmony.ui.home.AudioProcessor;
import com.example.vocalharmony.ui.home.SNRBar;

import java.util.Locale;

public class TrainingFragment extends Fragment implements AudioProcessor.VoiceQualityTestingCallback {

    private static final String TAG = "TrainingFragment";

    // --- UI Elements ---
    private Button voiceQualityButton;
    private Button wordEnunciationButton;
    private SNRBar snrBar;
    private ImageView micStatusIndicator;
    private TextView textCurrentSNRValue;
    private TextView textMaxSNRValue;
    private Button buttonStartSnr;
    private Button buttonStopSnr;
    private Button buttonReset;

    // --- State & Logic Variables ---
    private AudioProcessor audioProcessor;
    private boolean isTesting = false;
    private double maxSnrValueSession = Double.NEGATIVE_INFINITY;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> requestPermissionLauncher;


    // --- Fragment Lifecycle & Setup ---

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    updateMicIndicator(isGranted);
                    if (isGranted) {
                        Log.d(TAG, "✅ Permission granted via launcher.");
                        initializeAudioProcessor();
                        enableInteraction();
                    } else {
                        Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_LONG).show();
                        Log.d(TAG, "❌ Permission denied via launcher.");
                        disableInteraction();
                    }
                });
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_training, container, false);
        initializeUIComponents(rootView);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkMicrophonePermission();
        resetUIState();
    }


    // --- UI Initialization ---

    private void initializeUIComponents(View rootView) {
        voiceQualityButton = rootView.findViewById(R.id.button_voice_quality);
        wordEnunciationButton = rootView.findViewById(R.id.button_word_enunciation);
        snrBar = rootView.findViewById(R.id.snr_bar);
        micStatusIndicator = rootView.findViewById(R.id.mic_status_indicator);
        textCurrentSNRValue = rootView.findViewById(R.id.text_current_snr_value);
        textMaxSNRValue = rootView.findViewById(R.id.text_max_snr_value);
        buttonStartSnr = rootView.findViewById(R.id.button_start_snr);
        buttonStopSnr = rootView.findViewById(R.id.button_stop_snr);
        buttonReset = rootView.findViewById(R.id.button_reset);

        buttonStartSnr.setOnClickListener(v -> startSNRTest());
        buttonStopSnr.setOnClickListener(v -> stopSNRTest());
        buttonReset.setOnClickListener(v -> resetTest());

        voiceQualityButton.setOnClickListener(view -> {
            Log.d(TAG, "Voice Quality button clicked.");
            Toast.makeText(getContext(), "Voice Quality action TBD", Toast.LENGTH_SHORT).show();
        });

        wordEnunciationButton.setOnClickListener(view -> {
            Log.d(TAG, "Word Enunciation button clicked.");
            Toast.makeText(getContext(), "Word Enunciation action TBD", Toast.LENGTH_SHORT).show();
        });

        Log.d(TAG, "TrainingFragment UI components initialized.");
    }

    private void resetUIState() {
        if (snrBar != null) snrBar.reset();
        // Use getString() for safety
        String defaultValue = getString(R.string.snr_default_value);
        if (textCurrentSNRValue != null) textCurrentSNRValue.setText(defaultValue);
        if (textMaxSNRValue != null) textMaxSNRValue.setText(defaultValue);
        maxSnrValueSession = Double.NEGATIVE_INFINITY;

        if (buttonStopSnr != null) buttonStopSnr.setVisibility(View.GONE);
        if (buttonStartSnr != null) buttonStartSnr.setVisibility(View.VISIBLE);

        boolean hasPermission = hasAudioPermission();
        updateMicIndicator(hasPermission);
        if(buttonStartSnr != null) buttonStartSnr.setEnabled(hasPermission);
        if(voiceQualityButton != null) voiceQualityButton.setEnabled(true);
        if(wordEnunciationButton != null) wordEnunciationButton.setEnabled(true);
        if(buttonReset != null) buttonReset.setEnabled(hasPermission);
    }

    // --- Permission Handling ---

    private void checkMicrophonePermission() {
        if (!hasAudioPermission()) {
            Log.d(TAG, "Microphone permission not granted. Requesting...");
            requestAudioPermission();
            updateMicIndicator(false);
            disableInteraction();
        } else {
            Log.d(TAG, "Microphone permission already granted.");
            updateMicIndicator(true);
            initializeAudioProcessor();
            enableInteraction();
        }
    }

    private boolean hasAudioPermission() {
        if (getContext() == null) return false;
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void updateMicIndicator(boolean hasPermission) {
        if (micStatusIndicator != null) {
            micStatusIndicator.setImageResource(hasPermission ? R.drawable.ic_mic_on : R.drawable.ic_mic_off);
        }
    }

    private void disableInteraction() {
        mainHandler.post(() -> {
            if(buttonStartSnr != null) buttonStartSnr.setEnabled(false);
            if(buttonStopSnr != null) buttonStopSnr.setEnabled(false);
            if(buttonReset != null) buttonReset.setEnabled(false);
            if(voiceQualityButton != null) voiceQualityButton.setEnabled(false);
            if(wordEnunciationButton != null) wordEnunciationButton.setEnabled(false);
        });
    }

    private void enableInteraction() {
        mainHandler.post(() -> {
            boolean hasPermission = hasAudioPermission();
            if(buttonStartSnr != null) buttonStartSnr.setEnabled(hasPermission && !isTesting);
            if(buttonStopSnr != null) buttonStopSnr.setEnabled(hasPermission && isTesting);
            if(buttonReset != null) buttonReset.setEnabled(hasPermission && !isTesting);
            if(voiceQualityButton != null) voiceQualityButton.setEnabled(true);
            if(wordEnunciationButton != null) wordEnunciationButton.setEnabled(true);
        });
    }

    // --- AudioProcessor Initialization ---

    private void initializeAudioProcessor() {
        // Use null check instead of isReady() if instance already exists
        if (audioProcessor != null) {
            Log.d(TAG, "AudioProcessor instance already exists.");
            // If it exists, assume it's ready or handle re-initialization if needed
            // Maybe call enableInteraction() just in case state was weird
            enableInteraction();
            return;
        }
        // --- Context and Permission checks ---
        if (getContext() == null) { /* ... error handling ... */ return; }
        if (!hasAudioPermission()){ /* ... error handling ... */ return; }

        Log.d(TAG, "Initializing AudioProcessor...");
        try {
            audioProcessor = new AudioProcessor(requireContext(), this, null);
            // *** UPDATED CHECK HERE ***
            if (!audioProcessor.isReady()) { // Use the new method from AudioProcessor
                throw new IllegalStateException("AudioProcessor failed to initialize AudioRecord.");
            }
            Log.d(TAG, "AudioProcessor initialized successfully.");
            enableInteraction();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing AudioProcessor: " + e.getMessage(), e);
            Toast.makeText(requireContext(), R.string.audio_processor_error, Toast.LENGTH_SHORT).show();
            audioProcessor = null;
            disableInteraction();
        }
    }

    // --- SNR Test Control ---

    private void startSNRTest() {
        // *** UPDATED CHECK HERE ***
        if (audioProcessor == null || !audioProcessor.isReady()) { // Use the new method here
            Log.e(TAG, "Cannot start test: AudioProcessor not ready.");
            Toast.makeText(requireContext(), "Audio system not ready. Check permissions.", Toast.LENGTH_SHORT).show();
            checkMicrophonePermission();
            return;
        }
        if (isTesting) return;

        Log.d(TAG, "Starting SNR Test...");
        resetUIState();
        isTesting = true;
        audioProcessor.testMicrophone();

        mainHandler.post(() -> {
            if(buttonStartSnr != null) buttonStartSnr.setVisibility(View.GONE);
            if(buttonStopSnr != null) {
                buttonStopSnr.setVisibility(View.VISIBLE);
                buttonStopSnr.setEnabled(true);
            }
            if(voiceQualityButton != null) voiceQualityButton.setEnabled(false);
            if(wordEnunciationButton != null) wordEnunciationButton.setEnabled(false);
            if(buttonReset != null) buttonReset.setEnabled(false);
        });
    }

    private void stopSNRTest() {
        if (!isTesting || audioProcessor == null) {
            return;
        }
        Log.d(TAG, "Stopping SNR Test...");
        isTesting = false;
        audioProcessor.stopTesting();

        mainHandler.post(() -> {
            if(buttonStopSnr != null) buttonStopSnr.setVisibility(View.GONE);
            if(buttonStartSnr != null) buttonStartSnr.setVisibility(View.VISIBLE);
            enableInteraction();
        });
    }

    private void resetTest() {
        Log.d(TAG, "Resetting SNR test state and UI.");
        if (isTesting) {
            stopSNRTest();
        }
        resetUIState();
        Toast.makeText(requireContext(), "Test reset.", Toast.LENGTH_SHORT).show();
    }


    // --- AudioProcessor.VoiceQualityTestingCallback Implementation ---

    @Override
    public void onIntermediateSNR(final double snr) {
        mainHandler.post(() -> {
            if (!isAdded() || getContext() == null) return;

            if (snrBar != null) {
                snrBar.setSNRValue((float) snr);
            }

            String defaultValue = getString(R.string.snr_default_value); // Use getString here too
            if (textCurrentSNRValue != null) {
                if(Double.isFinite(snr)) {
                    textCurrentSNRValue.setText(String.format(Locale.getDefault(), "%.1f dB", snr));
                } else {
                    textCurrentSNRValue.setText(defaultValue);
                }
            }

            if (Double.isFinite(snr)) {
                maxSnrValueSession = Math.max(maxSnrValueSession, snr);
            }

            if (textMaxSNRValue != null) {
                if (maxSnrValueSession > Double.NEGATIVE_INFINITY) {
                    textMaxSNRValue.setText(String.format(Locale.getDefault(), "%.1f dB", maxSnrValueSession));
                } else {
                    textMaxSNRValue.setText(defaultValue);
                }
            }
        });
    }

    @Override
    public void onMicrophoneActive(final boolean isActive) {
        // Log.d(TAG, "Microphone Active State: " + isActive);
    }

    // --- Fragment Lifecycle Cleanup ---

    @Override
    public void onPause() {
        super.onPause();
        if (isTesting) {
            Log.w(TAG, "onPause: Fragment paused during SNR test. Stopping test.");
            stopSNRTest();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Releasing AudioProcessor and cleaning up views.");
        if (audioProcessor != null) {
            audioProcessor.release(); // Call the release method in AudioProcessor
            audioProcessor = null;
        }
        // Nullify view references
        snrBar = null;
        micStatusIndicator = null;
        textCurrentSNRValue = null;
        textMaxSNRValue = null;
        buttonStartSnr = null;
        buttonStopSnr = null;
        buttonReset = null;
        voiceQualityButton = null;
        wordEnunciationButton = null;
        mainHandler.removeCallbacksAndMessages(null);
    }
}