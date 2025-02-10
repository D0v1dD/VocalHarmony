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
    private Button recordBaselineButton;
    private Button startRecordButton;
    private Button stopRecordButton;
    private Button viewSavedFilesButton;

    // Flag to track if baseline recording is in progress
    private boolean isRecordingBaseline = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);

        // 1. Initialize UI
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
     * Set up UI elements: SNR bar + baseline/record/stop buttons.
     */
    private void initializeUIComponents(View rootView) {
        snrBar = rootView.findViewById(R.id.snr_bar);

        // Initialize the bar so it doesn't show "Excellent" from the start:
        snrBar.setSNRValue(-1.0);  // e.g. -1 dB means "Very Poor"

        recordBaselineButton  = rootView.findViewById(R.id.button_record_baseline);
        startRecordButton     = rootView.findViewById(R.id.button_start_record);
        stopRecordButton      = rootView.findViewById(R.id.button_stop_record);
        viewSavedFilesButton  = rootView.findViewById(R.id.button_view_saved_files);

        // Hide Stop button initially
        stopRecordButton.setVisibility(View.GONE);

        // Assign listeners
        recordBaselineButton.setOnClickListener(v -> recordBaseline());
        startRecordButton.setOnClickListener(v -> startRecording());
        stopRecordButton.setOnClickListener(v -> stopRecording());
        viewSavedFilesButton.setOnClickListener(v -> viewSavedBaselineFiles());
    }

    /**
     * Prompt user for RECORD_AUDIO permission if not granted.
     */
    private void requestRecordAudioPermission() {
        Log.d(TAG, "Requesting audio recording permission...");
        requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION
        );
    }

    /**
     * Handle permission dialog results.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionToRecordAccepted) {
                initializeAudioProcessor();
                Toast.makeText(requireContext(),
                        "Permission granted. You can record audio now.",
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Audio recording permission granted by user.");
            } else {
                Toast.makeText(requireContext(),
                        "Recording permission is required.",
                        Toast.LENGTH_LONG).show();
                Log.w(TAG, "Audio recording permission denied by user.");
            }
        }
    }

    /**
     * Create the AudioProcessor and connect it to our RecordingCallback.
     */
    private void initializeAudioProcessor() {
        Log.d(TAG, "Initializing AudioProcessor...");
        audioProcessor = new AudioProcessor(requireContext(), this);
    }

    // -----------------------------------------------------------------------
    // AudioProcessor.RecordingCallback Implementation

    @Override
    public void onAudioDataReceived(short[] audioBuffer) {
        // Ignoring waveform or amplitude graph, focusing on the bar only.
    }

    @Override
    public void onBaselineRecorded() {
        Log.d(TAG, "Baseline recording completed.");

        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        "Baseline recorded successfully.",
                        Toast.LENGTH_SHORT).show();
                // Enable start recording button
                startRecordButton.setEnabled(true);
            });
        }
    }

    @Override
    public void onSNRCalculated(double snrValue) {
        Log.d(TAG, "SNR Value (dB): " + snrValue);

        // Update the SNR bar if the fragment is still attached
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> snrBar.setSNRValue(snrValue));
        }
    }

    // -----------------------------------------------------------------------
    // Button Handlers

    /**
     * Record baseline (5 seconds).
     */
    private void recordBaseline() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }
        if (audioProcessor == null) {
            Toast.makeText(requireContext(),
                    "Audio Processor not initialized.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        isRecordingBaseline = true;
        audioProcessor.recordBaseline();

        // Update UI: disable other buttons, show stop
        requireActivity().runOnUiThread(() -> {
            recordBaselineButton.setEnabled(false);
            startRecordButton.setEnabled(false);
            stopRecordButton.setVisibility(View.VISIBLE);
            stopRecordButton.setEnabled(true);
        });

        Log.d(TAG, "Baseline recording started...");
    }

    /**
     * Start main audio recording (requires a valid baseline).
     */
    private void startRecording() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }
        if (audioProcessor == null) {
            Toast.makeText(requireContext(),
                    "Audio Processor not initialized.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!audioProcessor.isBaselineRecorded()) {
            Toast.makeText(requireContext(),
                    "Please record baseline first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        audioProcessor.startRecording();

        // UI updates
        requireActivity().runOnUiThread(() -> {
            recordBaselineButton.setEnabled(false);
            startRecordButton.setEnabled(false);
            stopRecordButton.setVisibility(View.VISIBLE);
            stopRecordButton.setEnabled(true);
        });

        Log.d(TAG, "Audio recording started...");
    }

    /**
     * Stop baseline or main recording.
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
                recordBaselineButton.setEnabled(true);
                startRecordButton.setEnabled(true);
            });
        } else {
            Log.w(TAG, "AudioProcessor is null, cannot stop recording.");
        }
    }

    /**
     * Show a list of files in getExternalFilesDir().
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
                            Toast.makeText(requireContext(),
                                    "Selected file: " + selectedFile,
                                    Toast.LENGTH_SHORT).show();
                        })
                        .show();
            } else {
                Toast.makeText(requireContext(),
                        "No saved baseline files found.",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(),
                    "Directory not found.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle Cleanup

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop audio if user leaves the fragment
        if (audioProcessor != null) {
            audioProcessor.stopRecording();
            Log.d(TAG, "Stopped recording in onDestroyView() to avoid fragment-not-attached crash.");
        }
    }
}
