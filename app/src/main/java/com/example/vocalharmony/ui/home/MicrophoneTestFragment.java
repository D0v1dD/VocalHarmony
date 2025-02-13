package com.example.vocalharmony.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.google.android.material.button.MaterialButton;

public class MicrophoneTestFragment extends Fragment implements AudioProcessor.MicrophoneTestTestingCallback {

    private static final String TAG = "MicrophoneTestFragment";
    private AudioProcessor audioProcessor;
    private MaterialButton buttonStartTest;
    private MaterialButton buttonStopTest;
    private MaterialButton buttonRecordBaseline;
    private TextView testFeedback;
    private TextView baselineQualityLabel;
    private TextView baselineQualityLevel;

    private boolean isBaselineRecorded = false;
    private boolean isTesting = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_microphone_test, container, false);
        initializeViews(view);
        setupAudioProcessor();
        setupButtonListeners();
        return view;
    }

    private void initializeViews(View view) {
        testFeedback = view.findViewById(R.id.test_feedback);
        buttonStartTest = view.findViewById(R.id.button_start_test);
        buttonStopTest = view.findViewById(R.id.button_stop_test);
        buttonRecordBaseline = view.findViewById(R.id.button_record_baseline);
        baselineQualityLabel = view.findViewById(R.id.baseline_quality_label);
        baselineQualityLevel = view.findViewById(R.id.baseline_quality_level);

        buttonStopTest.setVisibility(View.GONE);
    }

    private void setupAudioProcessor() {
        audioProcessor = new AudioProcessor(requireContext(), null, this);
    }

    private void setupButtonListeners() {
        buttonRecordBaseline.setOnClickListener(v -> recordBaseline());
        buttonStartTest.setOnClickListener(v -> startMicrophoneTest());
        buttonStopTest.setOnClickListener(v -> stopMicrophoneTest());
    }

    private void recordBaseline() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestMicrophonePermission();
            return;
        }

        if (audioProcessor == null) {
            Log.e(TAG, "❌ AudioProcessor is NULL! Cannot start baseline recording.");
            Toast.makeText(requireContext(), "Audio Processor not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "🎤 Recording baseline noise...");
        buttonRecordBaseline.setEnabled(false);
        buttonRecordBaseline.setText(R.string.recording_baseline);

        audioProcessor.startBaselineRecording();
    }

    private void startMicrophoneTest() {
        if (!isBaselineRecorded) {
            Toast.makeText(requireContext(), R.string.record_baseline_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (audioProcessor != null && !isTesting) {
            isTesting = true;
            buttonStartTest.setEnabled(false);
            buttonStopTest.setVisibility(View.VISIBLE);
            audioProcessor.testMicrophone();
        }
    }

    private void stopMicrophoneTest() {
        if (audioProcessor != null && isTesting) {
            isTesting = false;
            buttonStartTest.setEnabled(true);
            buttonStopTest.setVisibility(View.GONE);
            audioProcessor.stopTesting();
        }
    }

    private void requestMicrophonePermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.RECORD_AUDIO},
                200);
    }

    @Override
    public void onBaselineRecorded() {
        requireActivity().runOnUiThread(() -> {
            isBaselineRecorded = true;
            buttonRecordBaseline.setText(R.string.baseline_recorded);
            buttonRecordBaseline.setEnabled(false);
            Toast.makeText(requireContext(), R.string.baseline_recorded_toast, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "✅ Baseline noise recording completed.");
        });
    }

    @Override
    public void onBaselineQuality(String qualityLabel, int qualityLevel) {
        requireActivity().runOnUiThread(() -> {
            if (baselineQualityLabel != null) {
                baselineQualityLabel.setText(getString(R.string.baseline_quality_label) + ": " + qualityLabel);
            }
            if (baselineQualityLevel != null) {
                baselineQualityLevel.setText(getString(R.string.baseline_quality_level) + ": " + qualityLevel);
            }
            Log.d(TAG, "Baseline Quality: " + qualityLabel + ", Level: " + qualityLevel);
        });
    }

    @Override
    public void onMicrophoneActive(boolean isActive) {
        requireActivity().runOnUiThread(() -> {
            Log.d(TAG, isActive ? "Microphone is now active" : "Microphone is now inactive");
            buttonRecordBaseline.setEnabled(!isActive);
            buttonStartTest.setEnabled(!isActive && isBaselineRecorded);
        });
    }
}
