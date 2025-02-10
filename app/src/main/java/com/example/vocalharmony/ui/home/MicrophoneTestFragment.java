package com.example.vocalharmony.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.google.android.material.button.MaterialButton;

public class MicrophoneTestFragment extends Fragment implements AudioProcessor.TestingCallback {

    private static final String TAG = "MicrophoneTestFragment";
    // Adjust this threshold based on real-world electrolarynx amplitude tests
    private static final double AMPLITUDE_THRESHOLD = 0.02;

    private AudioProcessor audioProcessor;
    private MaterialButton buttonStartTest;
    private TextView testFeedback;
    private LinearLayout advancedInfoLayout;
    private TextView amplitudeInfo;
    private TextView frequencyInfo;
    private MaterialButton buttonToggleAdvanced;
    private boolean advancedVisible = false;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_microphone_test, container, false);

        initializeViews(view);
        setupAudioProcessor();
        setupButtonListeners();

        return view;
    }

    /**
     * Initialize all UI elements from the inflated layout.
     */
    private void initializeViews(View view) {
        testFeedback = view.findViewById(R.id.test_feedback);
        buttonStartTest = view.findViewById(R.id.button_start_test);
        advancedInfoLayout = view.findViewById(R.id.advanced_info_layout);
        amplitudeInfo = view.findViewById(R.id.amplitude_info);
        frequencyInfo = view.findViewById(R.id.frequency_info);
        buttonToggleAdvanced = view.findViewById(R.id.button_toggle_advanced);

        // Hide advanced info layout initially
        advancedInfoLayout.setVisibility(View.GONE);
        testFeedback.setVisibility(View.GONE);
    }

    /**
     * Create an AudioProcessor with a null RecordingCallback since
     * we're only using the TestingCallback in this fragment.
     */
    private void setupAudioProcessor() {
        audioProcessor = new AudioProcessor(requireContext(), null);
    }

    /**
     * Assign click listeners for buttons.
     */
    private void setupButtonListeners() {
        buttonStartTest.setOnClickListener(v -> startMicrophoneTest());
        buttonToggleAdvanced.setOnClickListener(v -> toggleAdvancedInfo());
    }

    /**
     * Start the 3-second microphone test using AudioProcessor.testMicrophone().
     * Resets UI state before initiating the test.
     */
    private void startMicrophoneTest() {
        // (Optional) Check if RECORD_AUDIO permission is granted, if you'd like to handle it here:
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Recording permission not granted. Prompt user or request permission.");
            // You could request the permission here or show a rationale dialog.
            return;
        }

        if (audioProcessor != null) {
            resetUIState();
            audioProcessor.testMicrophone(this);
            Log.d(TAG, "Microphone test started...");
        } else {
            Log.e(TAG, "AudioProcessor is null, cannot start test.");
        }
    }

    /**
     * Resets UI elements to a "testing in progress" state.
     */
    private void resetUIState() {
        requireActivity().runOnUiThread(() -> {
            testFeedback.setVisibility(View.GONE);
            advancedInfoLayout.setVisibility(View.GONE);

            buttonStartTest.setEnabled(false);
            buttonStartTest.setText(R.string.testing_in_progress);

            amplitudeInfo.setText("");
            frequencyInfo.setText("");
        });
    }

    private void toggleAdvancedInfo() {
        advancedVisible = !advancedVisible;
        advancedInfoLayout.setVisibility(advancedVisible ? View.VISIBLE : View.GONE);
        buttonToggleAdvanced.setText(advancedVisible
                ? getString(R.string.hide_advanced_info)
                : getString(R.string.show_advanced_info)
        );
    }

    // ------------------------------------------------------------------------
    // AudioProcessor.TestingCallback implementations

    @Override
    public void onTestingDataReceived(short[] audioBuffer) {
        // Optional: Add real-time waveform or amplitude visualization
        Log.d(TAG, "Processing audio buffer, size=" + audioBuffer.length);
    }

    @Override
    public void onTestCompleted(double amplitude, double dominantFrequency) {
        // Called once the 3-second microphone test finishes
        requireActivity().runOnUiThread(() -> {
            updateTestResults(amplitude, dominantFrequency);
            restoreUIState();
        });
    }

    /**
     * Display final amplitude/frequency test results and a simple "suitable/unsuitable" message.
     */
    private void updateTestResults(double amplitude, double frequency) {
        String feedback = (amplitude < AMPLITUDE_THRESHOLD)
                ? getString(R.string.mic_unsuitable)
                : getString(R.string.mic_suitable);

        testFeedback.setText(feedback);
        amplitudeInfo.setText(String.format("Amplitude: %.2f", amplitude));
        frequencyInfo.setText(String.format("Dominant Frequency: %.1f Hz", frequency));
    }

    /**
     * Restore UI elements after the test completes.
     */
    private void restoreUIState() {
        buttonStartTest.setEnabled(true);
        buttonStartTest.setText(R.string.start_test);
        testFeedback.setVisibility(View.VISIBLE);

        if (advancedVisible) {
            advancedInfoLayout.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------------
    // Lifecycle method for cleaning up

    /**
     * Stop the audio test if the user leaves this fragment (e.g., navigates away).
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (audioProcessor != null) {
            // Since testMicrophone() uses audioRecord internally,
            // calling stopRecording() will stop that thread and release the mic.
            audioProcessor.stopRecording();
            Log.d(TAG, "Microphone test stopped due to fragment destruction.");
        }
    }
}
