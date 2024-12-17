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

    private boolean permissionToRecordAccepted = false;

    private AudioProcessor audioProcessor;
    private LineChart audioChart;
    private LineDataSet dataSet;
    private LineData lineData;
    private SNRBar snrBar;

    private Button recordBaselineButton;
    private Button startRecordButton;
    private Button stopRecordButton;
    private Button viewSavedFilesButton;

    // Time tracking for graph updates
    private long lastGraphUpdateTime = 0;
    private static final int GRAPH_UPDATE_INTERVAL = 100; // milliseconds

    // Flag to track if baseline recording is in progress
    private boolean isRecordingBaseline = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the Voice Quality Fragment layout
        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);

        // Initialize UI components and set up event listeners
        initializeUIComponents(rootView);

        // Check and request audio recording permission if not granted
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission();
        } else {
            permissionToRecordAccepted = true;
            initializeAudioProcessor();
        }

        return rootView;
    }

    private void initializeUIComponents(View rootView) {
        // Initialize buttons and SNRBar
        recordBaselineButton = rootView.findViewById(R.id.button_record_baseline);
        startRecordButton = rootView.findViewById(R.id.button_start_record);
        stopRecordButton = rootView.findViewById(R.id.button_stop_record);
        viewSavedFilesButton = rootView.findViewById(R.id.button_view_saved_files);
        snrBar = rootView.findViewById(R.id.snr_bar);

        // Initialize the line chart used for audio visualization
        initializeChart(rootView);

        // Set up button click listeners
        recordBaselineButton.setOnClickListener(view -> recordBaseline());
        startRecordButton.setOnClickListener(view -> startRecording());
        stopRecordButton.setOnClickListener(view -> stopRecording());
        viewSavedFilesButton.setOnClickListener(view -> viewSavedBaselineFiles());

        // Initially, stop recording button is not visible
        stopRecordButton.setVisibility(View.GONE);
    }

    private void initializeAudioProcessor() {
        Log.d(TAG, "Initializing AudioProcessor.");
        audioProcessor = new AudioProcessor(requireContext(), new AudioProcessor.RecordingCallback() {
            @Override
            public void onAudioDataReceived(short[] audioBuffer) {
                // Update the graph with incoming audio data
                updateGraph(audioBuffer);
            }

            @Override
            public void onBaselineRecorded() {
                Log.d(TAG, "Baseline recording completed.");
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Baseline recorded successfully.", Toast.LENGTH_SHORT).show();
                    // After baseline is recorded, enable start recording button
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

    private void initializeChart(View rootView) {
        audioChart = rootView.findViewById(R.id.audio_chart);
        dataSet = new LineDataSet(new ArrayList<>(), "Audio Levels");
        dataSet.setDrawCircles(false);
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        lineData = new LineData(dataSet);
        audioChart.setData(lineData);

        audioChart.getDescription().setEnabled(false);
        audioChart.getLegend().setEnabled(false);
        audioChart.setTouchEnabled(false);
        audioChart.setViewPortOffsets(0, 0, 0, 0);
    }

    private void updateGraph(short[] buffer) {
        long currentTime = System.currentTimeMillis();
        // Throttle graph updates to every GRAPH_UPDATE_INTERVAL ms
        if (currentTime - lastGraphUpdateTime < GRAPH_UPDATE_INTERVAL) {
            return;
        }
        lastGraphUpdateTime = currentTime;

        requireActivity().runOnUiThread(() -> {
            int currentX = dataSet.getEntryCount();

            for (short amplitude : buffer) {
                float normalizedAmplitude = amplitude / 32768f;
                dataSet.addEntry(new Entry(currentX++, normalizedAmplitude));
            }

            // Limit data points to avoid performance degradation
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

    private void updateSNRBar(double snrValue) {
        // Update the SNR bar on the UI thread
        requireActivity().runOnUiThread(() -> snrBar.setSNRValue(snrValue));
    }

    private void recordBaseline() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }

        if (audioProcessor != null) {
            isRecordingBaseline = true;
            audioProcessor.recordBaseline();

            // Update UI to reflect baseline recording in progress
            requireActivity().runOnUiThread(() -> {
                recordBaselineButton.setEnabled(false);
                startRecordButton.setEnabled(false);
                stopRecordButton.setVisibility(View.VISIBLE);
                stopRecordButton.setEnabled(true);
            });
            Log.d(TAG, "Baseline recording started.");
        } else {
            Toast.makeText(requireContext(), "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Attempted to record baseline but AudioProcessor was null.");
        }
    }

    private void startRecording() {
        if (!permissionToRecordAccepted) {
            requestRecordAudioPermission();
            return;
        }

        if (audioProcessor != null) {
            if (!audioProcessor.isBaselineRecorded()) {
                Toast.makeText(requireContext(), "Please record baseline noise first.", Toast.LENGTH_SHORT).show();
                return;
            }

            audioProcessor.startRecording();
            // Update UI to indicate active recording
            requireActivity().runOnUiThread(() -> {
                startRecordButton.setEnabled(false);
                recordBaselineButton.setEnabled(false);
                stopRecordButton.setVisibility(View.VISIBLE);
                stopRecordButton.setEnabled(true);
            });
            Log.d(TAG, "Start recording initiated.");
        } else {
            Toast.makeText(requireContext(), "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Attempted to start recording but AudioProcessor was null.");
        }
    }

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
            Log.w(TAG, "Attempted to stop recording but AudioProcessor was null.");
        }
    }

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
                            // Optionally, implement logic to process or analyze the selected file.
                        })
                        .show();
            } else {
                Toast.makeText(requireContext(), "No saved baseline files found", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "Directory not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestRecordAudioPermission() {
        Log.d(TAG, "Requesting audio recording permission.");
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionToRecordAccepted) {
                initializeAudioProcessor();
                Toast.makeText(requireContext(), "Permission granted, you can now record audio.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Audio recording permission granted.");
            } else {
                Toast.makeText(requireContext(), "Recording permission is required", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Audio recording permission denied by user.");
            }
        }
    }
}
