package com.example.vocalharmony.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.util.ArrayList;

public class VoiceQualityFragment extends Fragment {

    private static final String TAG = "VoiceQualityFragment";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Permissions
    private boolean permissionToRecordAccepted = false;

    // Audio processing
    private AudioProcessor audioProcessor;

    // UI references
    private SNRBar snrBar;
    private LineChart audioChart;
    private Button recordBaselineButton;
    private Button startRecordButton;
    private Button stopRecordButton;
    private Button viewSavedFilesButton;

    // Graph update timing
    private long lastGraphUpdateTime = 0;
    private static final int GRAPH_UPDATE_INTERVAL = 100; // milliseconds

    // Flag to track if baseline recording is in progress
    private boolean isRecordingBaseline = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the updated fragment layout
        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);

        // Initialize UI components (SNR bar, chart, and buttons)
        initializeUIComponents(rootView);

        // Check/Request audio recording permission
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
     * Locate and set up UI elements (SNR bar, chart, buttons) from the inflated layout.
     */
    private void initializeUIComponents(View rootView) {
        // SNR Bar at the top
        snrBar = rootView.findViewById(R.id.snr_bar);

        // Audio Chart in the middle
        audioChart = rootView.findViewById(R.id.audio_chart);
        initializeChart();

        // Button references in the pinned LinearLayout at the bottom
        recordBaselineButton  = rootView.findViewById(R.id.button_record_baseline);
        startRecordButton     = rootView.findViewById(R.id.button_start_record);
        stopRecordButton      = rootView.findViewById(R.id.button_stop_record);
        viewSavedFilesButton  = rootView.findViewById(R.id.button_view_saved_files);

        // Initially hide the stop recording button
        stopRecordButton.setVisibility(View.GONE);

        // Assign click listeners
        recordBaselineButton.setOnClickListener(view -> recordBaseline());
        startRecordButton.setOnClickListener(view -> startRecording());
        stopRecordButton.setOnClickListener(view -> stopRecording());
        viewSavedFilesButton.setOnClickListener(view -> viewSavedBaselineFiles());
    }

    /**
     * Create and configure the chart for visualizing audio data.
     */
    private void initializeChart() {
        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), "Audio Levels");
        dataSet.setDrawCircles(false);
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        LineData lineData = new LineData(dataSet);
        audioChart.setData(lineData);

        // Disable extra UI features for a cleaner look
        audioChart.getDescription().setEnabled(false);
        audioChart.getLegend().setEnabled(false);

        // Disable user touch gestures on the chart
        audioChart.setTouchEnabled(false);

        // Remove default chart offsets, so the line extends to chart edges
        audioChart.setViewPortOffsets(0, 0, 0, 0);
    }

    /**
     * Request permission to record audio if not already granted.
     */
    private void requestRecordAudioPermission() {
        Log.d(TAG, "Requesting audio recording permission...");
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    /**
     * Initialize the audio processor used to handle baseline/noise calculations & SNR.
     */
    private void initializeAudioProcessor() {
        Log.d(TAG, "Initializing AudioProcessor...");
        audioProcessor = new AudioProcessor(requireContext(), new AudioProcessor.RecordingCallback() {
            @Override
            public void onAudioDataReceived(short[] audioBuffer) {
                // Update the chart with incoming audio data
                updateChart(audioBuffer);
            }

            @Override
            public void onBaselineRecorded() {
                Log.d(TAG, "Baseline recording completed.");
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Baseline recorded successfully.", Toast.LENGTH_SHORT).show();
                    // Enable start recording after baseline is done
                    startRecordButton.setEnabled(true);
                });
            }

            @Override
            public void onSNRCalculated(double snrValue) {
                Log.d(TAG, "SNR Value: " + snrValue);
                updateSNRBar(snrValue);
            }
        });
    }

    /**
     * Update the chart with live audio data. Throttled to GRAPH_UPDATE_INTERVAL ms.
     */
    private void updateChart(short[] buffer) {
        long currentTime = System.currentTimeMillis();
        // Throttle frequent updates
        if (currentTime - lastGraphUpdateTime < GRAPH_UPDATE_INTERVAL) {
            return;
        }
        lastGraphUpdateTime = currentTime;

        // Because MPAndroidChart updates must happen on the main thread
        requireActivity().runOnUiThread(() -> {
            if (audioChart.getData() == null) return; // Safety check
            LineData lineData = audioChart.getData();
            if (lineData.getDataSetCount() == 0) return; // Safety check for dataSet existence

            // Retrieve the existing DataSet
            LineDataSet dataSet = (LineDataSet) lineData.getDataSetByIndex(0);
            int currentX = dataSet.getEntryCount();

            // Convert short samples to normalized float
            for (short amplitude : buffer) {
                float normalizedAmplitude = amplitude / 32768f;
                dataSet.addEntry(new Entry(currentX++, normalizedAmplitude));
            }

            // Limit data points to keep performance stable
            int maxVisiblePoints = 500;
            while (dataSet.getEntryCount() > maxVisiblePoints) {
                dataSet.removeFirst();
            }

            dataSet.notifyDataSetChanged();
            lineData.notifyDataChanged();
            audioChart.notifyDataSetChanged();
            audioChart.invalidate();
        });
    }

    /**
     * Refresh the SNR bar when a new SNR value is calculated.
     */
    private void updateSNRBar(double snrValue) {
        requireActivity().runOnUiThread(() -> snrBar.setSNRValue(snrValue));
    }

    /**
     * Begins a baseline noise recording session.
     */
    private void recordBaseline() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }
        if (audioProcessor == null) {
            Toast.makeText(requireContext(), "Audio Processor not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }

        isRecordingBaseline = true;
        audioProcessor.recordBaseline();

        // Update UI
        requireActivity().runOnUiThread(() -> {
            recordBaselineButton.setEnabled(false);
            startRecordButton.setEnabled(false);
            stopRecordButton.setVisibility(View.VISIBLE);
            stopRecordButton.setEnabled(true);
        });
        Log.d(TAG, "Baseline recording started...");
    }

    /**
     * Starts actual audio recording (requires a prior baseline).
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
        if (!audioProcessor.isBaselineRecorded()) {
            Toast.makeText(requireContext(), "Please record baseline noise first.", Toast.LENGTH_SHORT).show();
            return;
        }

        audioProcessor.startRecording();
        // Update UI
        requireActivity().runOnUiThread(() -> {
            recordBaselineButton.setEnabled(false);
            startRecordButton.setEnabled(false);
            stopRecordButton.setVisibility(View.VISIBLE);
            stopRecordButton.setEnabled(true);
        });
        Log.d(TAG, "Audio recording started...");
    }

    /**
     * Stops either baseline or normal recording.
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
            // Restore button states
            requireActivity().runOnUiThread(() -> {
                stopRecordButton.setVisibility(View.GONE);
                stopRecordButton.setEnabled(false);
                recordBaselineButton.setEnabled(true);
                startRecordButton.setEnabled(true);
            });
        } else {
            Log.w(TAG, "Attempted to stop recording, but AudioProcessor is null.");
        }
    }

    /**
     * View saved baseline files in an alert dialog.
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
                            // Optional: logic to process the selected file
                        })
                        .show();
            } else {
                Toast.makeText(requireContext(), "No saved baseline files found", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "Directory not found", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle the result of requesting RECORD_AUDIO permission.
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
                Toast.makeText(requireContext(), "Permission granted, you can record audio now.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Audio recording permission granted by user.");
            } else {
                Toast.makeText(requireContext(), "Recording permission is required.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Audio recording permission denied by user.");
            }
        }
    }
}
