package com.example.vocalharmony.ui.record;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.FileOutputStream;
import java.io.IOException;

public class RecordYourselfFragment extends Fragment {

    private static final int SAMPLE_RATE = 44100; // Sample rate in Hz
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private String audioFilePath;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record_yourself, container, false);

        Button buttonRecord = root.findViewById(R.id.button_record);
        Button buttonStop = root.findViewById(R.id.button_stop);

        audioFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recorded_audio.pcm";

        buttonRecord.setOnClickListener(v -> {
            if (checkPermissions()) {
                startRecording();
            } else {
                requestPermissions();
            }
        });

        buttonStop.setOnClickListener(v -> {
            stopRecording();
        });

        return root;
    }

    private void startRecording() {
        Log.d("RecordYourselfFragment", "startRecording called");

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        audioRecord.startRecording();
        isRecording = true;

        new Thread(() -> {
            writeAudioDataToFile(bufferSize);
        }).start();

        Toast.makeText(getActivity(), "Recording started", Toast.LENGTH_SHORT).show();
    }

    private void writeAudioDataToFile(int bufferSize) {
        byte[] audioData = new byte[bufferSize];
        try (FileOutputStream os = new FileOutputStream(audioFilePath)) {
            while (isRecording) {
                int read = audioRecord.read(audioData, 0, bufferSize);
                if (read > 0) {
                    os.write(audioData, 0, read);
                }
            }
        } catch (IOException e) {
            Log.e("RecordYourselfFragment", "Error writing audio data", e);
        }
    }

    private void stopRecording() {
        if (isRecording) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            Toast.makeText(getActivity(), "Recording stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(getActivity(), new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, 200);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(getActivity(), "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
