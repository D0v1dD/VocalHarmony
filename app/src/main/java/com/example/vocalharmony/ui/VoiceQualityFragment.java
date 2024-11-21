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

    private long lastGraphUpdateTime = 0; // Last update time for graph
    private static final int GRAPH_UPDATE_INTERVAL = 100; // Update interval for graph (milliseconds)
    private boolean isRecordingBaseline = false; // Flag to track if baseline recording is in progress

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);

        // Initialize UI components
        initializeUIComponents(rootView);

        // Request RECORD_AUDIO permission if not granted
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
        // Initialize buttons
        recordBaselineButton = rootView.findViewById(R.id.button_record_baseline);
        startRecordButton = rootView.findViewById(R.id.button_start_record);
        stopRecordButton = rootView.findViewById(R.id.button_stop_record);
        viewSavedFilesButton = rootView.findViewById(R.id.button_view_saved_files);
        snrBar = rootView.findViewById(R.id.snr_bar);

        // Initialize chart
        initializeChart(rootView);

        // Set button listeners
        recordBaselineButton.setOnClickListener(view -> recordBaseline());
        startRecordButton.setOnClickListener(view -> startRecording());
        stopRecordButton.setOnClickListener(view -> stopRecording());
        viewSavedFilesButton.setOnClickListener(view -> viewSavedBaselineFiles());

        // Hide stop recording button initially
        stopRecordButton.setVisibility(View.GONE);
    }

    private void initializeAudioProcessor() {
        audioProcessor = new AudioProcessor(requireContext(), new AudioProcessor.RecordingCallback() {
            @Override
            public void onAudioDataReceived(short[] audioBuffer) {
                updateGraph(audioBuffer);
            }

            @Override
            public void onBaselineRecorded() {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Baseline recorded successfully.", Toast.LENGTH_SHORT).show();
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

            // Limit the number of points to prevent performance issues
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
            // Update button visibility
            requireActivity().runOnUiThread(() -> {
                recordBaselineButton.setEnabled(false);
                startRecordButton.setEnabled(false);
                stopRecordButton.setVisibility(View.VISIBLE);
                stopRecordButton.setEnabled(true);
            });
        } else {
            Toast.makeText(requireContext(), "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
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
            // Update button visibility
            requireActivity().runOnUiThread(() -> {
                startRecordButton.setEnabled(false);
                recordBaselineButton.setEnabled(false);
                stopRecordButton.setVisibility(View.VISIBLE);
                stopRecordButton.setEnabled(true);
            });
        } else {
            Toast.makeText(requireContext(), "Audio Processor is not initialized.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (audioProcessor != null) {
            if (isRecordingBaseline) {
                audioProcessor.stopBaselineRecording();
                isRecordingBaseline = false;
            } else {
                audioProcessor.stopRecording();
            }
            // Update button visibility
            requireActivity().runOnUiThread(() -> {
                stopRecordButton.setVisibility(View.GONE);
                stopRecordButton.setEnabled(false);
                recordBaselineButton.setEnabled(true);
                startRecordButton.setEnabled(true);
            });
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
            } else {
                Toast.makeText(requireContext(), "Recording permission is required", Toast.LENGTH_LONG).show();
            }
        }
    }
}
