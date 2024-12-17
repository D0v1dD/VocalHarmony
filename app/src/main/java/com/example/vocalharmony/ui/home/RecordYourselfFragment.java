package com.example.vocalharmony.ui.home;

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
import android.widget.Button;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class RecordYourselfFragment extends Fragment {

    private static final String TAG = "RecordYourselfFragment";
    private static final int REQUEST_PERMISSIONS = 200;

    private static final int SAMPLE_RATE = 44100; // Sample rate in Hz
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private String audioFilePath;

    private Button buttonRecord;
    private Button buttonStop;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record_yourself, container, false);

        buttonRecord = root.findViewById(R.id.button_record);
        buttonStop = root.findViewById(R.id.button_stop);

        // Instead of Environment.getExternalStorageDirectory(), use app-specific directory:
        File appExternalDir = requireContext().getExternalFilesDir(null);
        if (appExternalDir != null) {
            audioFilePath = appExternalDir.getAbsolutePath() + "/recorded_audio.pcm";
        } else {
            Log.w(TAG, "External files directory is null, using internal storage as fallback.");
            audioFilePath = requireContext().getFilesDir().getAbsolutePath() + "/recorded_audio.pcm";
        }

        buttonRecord.setOnClickListener(v -> {
            if (checkPermissions()) {
                startRecording();
            } else {
                requestPermissions();
            }
        });

        buttonStop.setOnClickListener(v -> stopRecording());

        return root;
    }

    private void startRecording() {
        Log.d(TAG, "startRecording() called");

        // Double-check RECORD_AUDIO permission before proceeding
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getActivity(), "Audio record permission not granted.", Toast.LENGTH_SHORT).show();
            requestPermissions(); // Or prompt the user again
            return;
        }

        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Toast.makeText(getActivity(), "Invalid buffer size.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Invalid buffer size returned.");
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(getActivity(), "AudioRecord initialization failed.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "AudioRecord initialization failed. Check parameters and permissions.");
                return;
            }

            audioRecord.startRecording();
            isRecording = true;

            // Start a new thread to write data to file
            new Thread(() -> writeAudioDataToFile(bufferSize)).start();

            Toast.makeText(getActivity(), "Recording started", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Recording started successfully.");
        } catch (SecurityException se) {
            // This handles the rare case that even with permission checks,
            // a SecurityException is thrown.
            Log.e(TAG, "SecurityException while initializing AudioRecord. Permission might be denied at runtime.", se);
            Toast.makeText(getActivity(), "Unable to start recording due to permissions.", Toast.LENGTH_SHORT).show();
        }
    }


    private void writeAudioDataToFile(int bufferSize) {
        byte[] audioData = new byte[bufferSize];

        try (FileOutputStream os = new FileOutputStream(audioFilePath)) {
            while (isRecording) {
                int read = audioRecord.read(audioData, 0, bufferSize);
                if (read > 0) {
                    os.write(audioData, 0, read);
                } else {
                    Log.w(TAG, "AudioRecord read returned " + read + ", possibly an error.");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing audio data to file: " + audioFilePath, e);
        }

        Log.d(TAG, "Finished writing audio data to file.");
    }

    private void stopRecording() {
        if (isRecording && audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            Toast.makeText(getActivity(), "Recording stopped", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Recording stopped and resources released.");
        } else {
            Log.d(TAG, "stopRecording() called but was not recording or audioRecord is null.");
        }
    }

    private boolean checkPermissions() {
        // Check only RECORD_AUDIO permission if we use app-specific external directories
        // WRITE_EXTERNAL_STORAGE is often not needed for getExternalFilesDir(),
        // but if you do need it, add it here.
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        // If you do need WRITE_EXTERNAL_STORAGE for other reasons, add it to this request as well.
        ActivityCompat.requestPermissions(
                requireActivity(),
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_PERMISSIONS
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean audioGranted = false;

            if (grantResults.length > 0) {
                audioGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            }

            if (audioGranted) {
                Log.d(TAG, "Audio permission granted by user.");
                startRecording();
            } else {
                Toast.makeText(getActivity(), "Permission denied", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Audio permission denied by user.");
            }
        }
    }
}
