package com.example.vocalharmony.ui.home; // Or your chosen package

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
// Removed unused IOException import

public class SpeechToTextFragment extends Fragment {

    private static final String TAG = "SpeechToTextFragment";

    // AudioRecord Configuration
    private static final int SAMPLE_RATE = 16000; // Rate suitable for STT
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // UI Elements
    private MaterialButton recordButton;
    private TextView sttResultTextView;
    private ProgressBar sttProgressBar;

    // State Management
    private boolean isRecording = false;
    private boolean hasAudioPermission = false;

    // Audio Handling
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private ByteArrayOutputStream recordingBuffer;

    // Permission Handling
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    hasAudioPermission = true;
                    if (recordButton != null) recordButton.setEnabled(true);
                    Toast.makeText(getContext(), "Permission Granted.", Toast.LENGTH_SHORT).show();
                } else {
                    hasAudioPermission = false;
                    if (recordButton != null) recordButton.setEnabled(false);
                    Toast.makeText(getContext(), "Permission Denied. Cannot record.", Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_speech_to_text, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recordButton = view.findViewById(R.id.button_start_stt);
        sttResultTextView = view.findViewById(R.id.textview_stt_result);
        sttProgressBar = view.findViewById(R.id.progress_bar_stt);

        checkPermissionsAndSetupUI();

        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
    }

    private void checkPermissionsAndSetupUI() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            hasAudioPermission = true;
            if (recordButton != null) recordButton.setEnabled(true);
        } else {
            hasAudioPermission = false;
            if (recordButton != null) recordButton.setEnabled(false);
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        if (!hasAudioPermission) {
            checkPermissionsAndSetupUI();
            return;
        }

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not be initialized.");
                return;
            }

            recordingBuffer = new ByteArrayOutputStream();
            audioRecord.startRecording();
            isRecording = true;
            sttResultTextView.setText(getString(R.string.stt_status_recording));
            recordButton.setText(getString(R.string.stt_button_stop));

            Log.i(TAG, "Recording started.");

            recordingThread = new Thread(this::writeAudioDataToBuffer, "AudioRecorder Thread");
            recordingThread.start();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting AudioRecord", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Permission error starting recorder.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void writeAudioDataToBuffer() {
        byte[] data = new byte[BUFFER_SIZE];
        while (isRecording) {
            int read = audioRecord.read(data, 0, BUFFER_SIZE);
            if (read > 0) {
                // No try-catch needed here because ByteArrayOutputStream.write() does not throw IOException
                recordingBuffer.write(data, 0, read);
            }
        }
    }

    private void stopRecording() {
        if (!isRecording || audioRecord == null) {
            return;
        }

        isRecording = false;
        recordButton.setText(getString(R.string.stt_button_start));
        recordButton.setEnabled(false);
        sttResultTextView.setText(getString(R.string.stt_status_processing));
        sttProgressBar.setVisibility(View.VISIBLE);

        try {
            if(recordingThread != null) recordingThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for recording thread", e);
            Thread.currentThread().interrupt();
        }

        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.stop();
        }
        audioRecord.release();
        audioRecord = null;
        recordingThread = null;

        Log.i(TAG, "Recording stopped. Total bytes captured: " + recordingBuffer.size());

        if(getContext() != null) {
            Toast.makeText(getContext(), "Captured " + recordingBuffer.size() + " bytes of audio.", Toast.LENGTH_SHORT).show();
        }
        recordButton.setEnabled(true);
        sttProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) {
            stopRecording();
        }
    }
}