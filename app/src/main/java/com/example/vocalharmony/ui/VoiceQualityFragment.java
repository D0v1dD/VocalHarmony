package com.example.vocalharmony.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.example.vocalharmony.ui.home.AudioProcessor;
import com.example.vocalharmony.ui.home.SNRBar;

import java.io.File;

public class VoiceQualityFragment extends Fragment implements AudioProcessor.RecordingCallback {
    private static final String TAG = "VoiceQualityFragment";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Permissions
    private boolean permissionToRecordAccepted = false;

    // Audio processing
    private AudioProcessor audioProcessor;

    // UI references
    private SNRBar snrBar;
    private Button recordEnvBaselineButton;     // Environment baseline button
    private Button recordDeviceBaselineButton;  // Device hum baseline button
    private Button startRecordButton;
    private Button stopRecordButton;
    private Button viewSavedFilesButton;

    // Flag to track if a baseline recording is in progress
    private boolean isRecordingBaseline = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);

        // 1. Initialize UI components
        initializeUIComponents(rootView);

        // 2. Check / request audio permission
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission();
        } else {
            permissionToRecordAccepted = true;
            initializeAudioProcessor();
        }

        return rootView;
    }

    /**
     * Find and set up UI elements.
     * (Ensure your XML layout defines buttons with the IDs shown below.)
     */
    private void initializeUIComponents(View rootView) {
        snrBar = rootView.findViewById(R.id.snr_bar);
        // Initialize the SNR bar to a neutral value (e.g., 0.0 dB)
        snrBar.setSNRValue(0.0f);

        recordEnvBaselineButton = rootView.findViewById(R.id.button_record_baseline);
        recordDeviceBaselineButton = rootView.findViewById(R.id.button_record_device_baseline);
        startRecordButton = rootView.findViewById(R.id.button_start_record);
        stopRecordButton = rootView.findViewById(R.id.button_stop_record);
        viewSavedFilesButton = rootView.findViewById(R.id.button_view_saved_files);

        // Hide the Stop button initially
        stopRecordButton.setVisibility(View.GONE);

        // Assign click listeners
        recordEnvBaselineButton.setOnClickListener(v -> recordEnvironmentBaseline());
        recordDeviceBaselineButton.setOnClickListener(v -> recordDeviceBaseline());
        startRecordButton.setOnClickListener(v -> startRecording());
        stopRecordButton.setOnClickListener(v -> stopRecording());
        viewSavedFilesButton.setOnClickListener(v -> viewSavedBaselineFiles());
    }

    /**
     * Request RECORD_AUDIO permission if not already granted.
     */
    private void requestRecordAudioPermission() {
        Log.d(TAG, "Requesting audio recording permission...");
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionToRecordAccepted) {
                initializeAudioProcessor();
                Toast.makeText(requireContext(), "Permission granted. You can record audio now.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Audio recording permission granted by user.");
            } else {
                Toast.makeText(requireContext(), "Recording permission is required.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Audio recording permission denied by user.");
            }
        }
    }

    /**
     * Initialize the AudioProcessor with this fragment as the callback.
     */
    private void initializeAudioProcessor() {
        Log.d(TAG, "Initializing AudioProcessor...");
        audioProcessor = new AudioProcessor(requireContext(), this);
    }

    // -----------------------------------------------------------------------
    // AudioProcessor.RecordingCallback Implementation

    @Override
    public void onAudioDataReceived(short[] audioBuffer) {
        // For this version, we're not displaying waveform data.
    }

    @Override
    public void onBaselineRecorded() {
        Log.d(TAG, "Baseline recording completed.");
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Baseline recorded successfully.", Toast.LENGTH_SHORT).show();
                // Enable the start recording button after baseline recording is complete
                startRecordButton.setEnabled(true);
                startRecordButton.setText("Activate SNR");
                // Optionally, hide the baseline buttons to avoid re-recording
                recordEnvBaselineButton.setVisibility(View.GONE);
                recordDeviceBaselineButton.setVisibility(View.GONE);
            });
        }
    }

    @Override
    public void onSNRCalculated(double snrValue) {
        Log.d(TAG, "SNR Value: " + snrValue);
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> snrBar.setSNRValue(snrValue));
        }
    }

    // -----------------------------------------------------------------------
    // Button Handlers

    /**
     * Record the environment baseline (e.g., 5 seconds).
     */
    private void recordEnvironmentBaseline() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }
        if (audioProcessor == null) {
            Toast.makeText(requireContext(), "Audio Processor not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }
        isRecordingBaseline = true;
        audioProcessor.recordEnvironmentBaseline();

        requireActivity().runOnUiThread(() -> {
            recordEnvBaselineButton.setEnabled(false);
            recordDeviceBaselineButton.setEnabled(false);
            startRecordButton.setEnabled(false);
            stopRecordButton.setVisibility(View.VISIBLE);
            stopRecordButton.setEnabled(true);
        });
        Log.d(TAG, "Environment baseline recording started...");
    }

    /**
     * Record the device hum baseline (e.g., 3 seconds).
     */
    private void recordDeviceBaseline() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }
        if (audioProcessor == null) {
            Toast.makeText(requireContext(), "Audio Processor not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }
        isRecordingBaseline = true;
        audioProcessor.recordDeviceHumBaseline();

        requireActivity().runOnUiThread(() -> {
            recordEnvBaselineButton.setEnabled(false);
            recordDeviceBaselineButton.setEnabled(false);
            startRecordButton.setEnabled(false);
            stopRecordButton.setVisibility(View.VISIBLE);
            stopRecordButton.setEnabled(true);
        });
        Log.d(TAG, "Device baseline recording started...");
    }

    /**
     * Start the main audio recording.
     * This method chooses which baseline to use (device or environment) based on what has been recorded.
     */
    private void startRecording() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }
        if (audioProcessor == null) {
            Toast.makeText(requireContext(), "Audio Processor not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Decide which baseline to use:
        if (audioProcessor.isDeviceHumBaselineRecorded()) {
            audioProcessor.setBaselineMode("device");
        } else if (audioProcessor.isEnvironmentBaselineRecorded()) {
            audioProcessor.setBaselineMode("environment");
        } else {
            Toast.makeText(requireContext(), "Please record a baseline first.", Toast.LENGTH_SHORT).show();
            return;
        }

        audioProcessor.startRecording();

        requireActivity().runOnUiThread(() -> {
            // Disable the baseline buttons and start button while recording
            recordEnvBaselineButton.setEnabled(false);
            recordDeviceBaselineButton.setEnabled(false);
            startRecordButton.setEnabled(false);
            stopRecordButton.setVisibility(View.VISIBLE);
            stopRecordButton.setEnabled(true);
        });
        Log.d(TAG, "Audio recording started.");
    }

    /**
     * Stop the current recording (baseline or main).
     */
    private void stopRecording() {
        if (audioProcessor != null) {
            if (isRecordingBaseline) {
                audioProcessor.stopBaselineRecording();
                isRecordingBaseline = false;
                Log.d(TAG, "Baseline recording stopped.");
            } else {
                audioProcessor.stopRecording();
                Log.d(TAG, "Audio recording stopped.");
            }
            requireActivity().runOnUiThread(() -> {
                stopRecordButton.setVisibility(View.GONE);
                stopRecordButton.setEnabled(false);
                // Re-enable baseline buttons so the user can record again if needed
                recordEnvBaselineButton.setVisibility(View.VISIBLE);
                recordEnvBaselineButton.setEnabled(true);
                recordDeviceBaselineButton.setVisibility(View.VISIBLE);
                recordDeviceBaselineButton.setEnabled(true);
                startRecordButton.setEnabled(true);
                startRecordButton.setText("Activate SNR");
            });
        } else {
            Log.w(TAG, "AudioProcessor is null, cannot stop recording.");
        }
    }

    /**
     * Display a list of saved baseline files.
     */
    private void viewSavedBaselineFiles() {
        File directory = requireContext().getExternalFilesDir(null);
        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null && files.length > 0) {
                String[] fileNames = new String[files.length];
                for (int i = 0; i < files.length; i++) {
                    fileNames[i] = files[i].getName();
                }
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Saved Baseline Files")
                        .setItems(fileNames, (dialog, which) -> {
                            String selectedFile = fileNames[which];
                            Toast.makeText(requireContext(), "Selected file: " + selectedFile, Toast.LENGTH_SHORT).show();
                        })
                        .show();
            } else {
                Toast.makeText(requireContext(), "No saved baseline files found.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "Directory not found.", Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle Cleanup

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (audioProcessor != null) {
            audioProcessor.stopRecording();
            Log.d(TAG, "Stopped recording in onDestroyView() to avoid fragment-not-attached crash.");
        }
    }
}
