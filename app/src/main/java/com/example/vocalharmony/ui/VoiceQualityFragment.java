package com.example.vocalharmony.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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

public class VoiceQualityFragment extends Fragment implements AudioProcessor.VoiceQualityTestingCallback {

    private static final String TAG = "VoiceQualityFragment";
    private AudioProcessor audioProcessor;
    private SNRBar snrBar;
    private Button startSNRButton;
    private Button stopRecordButton;
    private Button recordBaselineButton;
    private ImageView micStatusIndicator;

    private boolean isRecording = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                updateMicIndicator(isGranted);
                if (isGranted) {
                    initializeAudioProcessor();
                    Log.d(TAG, "✅ Permission granted, initializing audio processor.");
                } else {
                    Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "❌ Permission denied.");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_voice_quality, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeUIComponents(view);
        checkMicrophonePermission();
    }

    private void initializeUIComponents(View rootView) {
        snrBar = rootView.findViewById(R.id.snr_bar);
        snrBar.setSNRValue(0.0f);
        startSNRButton = rootView.findViewById(R.id.button_start_snr);
        stopRecordButton = rootView.findViewById(R.id.button_stop_record);
        recordBaselineButton = rootView.findViewById(R.id.button_record_baseline);
        micStatusIndicator = rootView.findViewById(R.id.mic_status_indicator);

        stopRecordButton.setVisibility(View.GONE);

        startSNRButton.setOnClickListener(v -> startSNRTest());
        stopRecordButton.setOnClickListener(v -> stopSNRTest());
        recordBaselineButton.setOnClickListener(v -> recordBaseline());
    }

    private void checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "🔄 Requesting microphone permission...");
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            updateMicIndicator(true);
            initializeAudioProcessor();
            Log.d(TAG, "✅ Microphone permission already granted.");
        }
    }

    private void updateMicIndicator(boolean hasPermission) {
        if (micStatusIndicator != null) {
            micStatusIndicator.setImageResource(hasPermission ? R.drawable.ic_mic_on : R.drawable.ic_mic_off);
        }
        if (!hasPermission) {
            disableRecordingButtons();
        }
    }

    private void disableRecordingButtons() {
        startSNRButton.setEnabled(false);
        recordBaselineButton.setEnabled(false);
    }

    private void initializeAudioProcessor() {
        if (audioProcessor == null) {
            Log.d(TAG, "🔧 Initializing AudioProcessor...");
            try {
                audioProcessor = new AudioProcessor(requireContext(), this, null);
            } catch (Exception e) {
                Log.e(TAG, "❌ Error initializing AudioProcessor: " + e.getMessage());
                Toast.makeText(requireContext(), R.string.audio_processor_error, Toast.LENGTH_SHORT).show();
                startSNRButton.setEnabled(false);
                recordBaselineButton.setEnabled(false);
            }
        } else {
            Log.d(TAG, "✅ AudioProcessor already initialized.");
        }
    }

    private void recordBaseline() {
        Toast.makeText(requireContext(), "Please record baseline in the Microphone Test section.", Toast.LENGTH_LONG).show();
    }

    private void startSNRTest() {
        if (audioProcessor == null) {
            Log.e(TAG, "audioProcessor is null");
            return;
        }
        isRecording = true;
        audioProcessor.testMicrophone();
        startSNRButton.setEnabled(false);
        stopRecordButton.setVisibility(View.VISIBLE);
    }

    private void stopSNRTest() {
        if (!isRecording || audioProcessor == null) {
            return;
        }
        isRecording = false;
        audioProcessor.stopTesting();
        stopRecordButton.setVisibility(View.GONE);
        startSNRButton.setEnabled(true);
    }

    @Override
    public void onIntermediateSNR(double snr) {
        if (snrBar != null) {
            snrBar.setSNRValue((float) snr);
        }
    }

    @Override
    public void onMicrophoneActive(boolean isActive) {
        requireActivity().runOnUiThread(() -> {
            startSNRButton.setEnabled(!isActive);
            recordBaselineButton.setEnabled(!isActive);
        });
    }
}
