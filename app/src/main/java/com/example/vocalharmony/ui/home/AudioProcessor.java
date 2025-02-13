package com.example.vocalharmony.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class AudioProcessor {

    private static final String TAG = "AudioProcessor";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // Windowing and Averaging Constants
    private static final int WINDOW_SIZE_MS = 100; // Window size in milliseconds
    private static final int OVERLAP_PERCENTAGE = 50; // Overlap percentage
    private final int windowSizeSamples;
    private final int overlapSamples;

    private AudioRecord audioRecord;
    private volatile boolean isTesting = false;
    private volatile boolean isBaselineRecording = false;
    private final Context context;
    private final VoiceQualityTestingCallback voiceQualityTestingCallback;
    private final MicrophoneTestTestingCallback microphoneTestTestingCallback;

    private double baselineNoisePower = 0.0; // Initialize to 0.0

    // Constructor (calculate window sizes)
    public AudioProcessor(Context context, VoiceQualityTestingCallback voiceQualityTestingCallback, MicrophoneTestTestingCallback microphoneTestTestingCallback) {
        this.context = context;
        this.voiceQualityTestingCallback = voiceQualityTestingCallback;
        this.microphoneTestTestingCallback = microphoneTestTestingCallback;

        // Calculate window and overlap sizes
        this.windowSizeSamples = (int) ((double) WINDOW_SIZE_MS / 1000 * SAMPLE_RATE);
        this.overlapSamples = windowSizeSamples * OVERLAP_PERCENTAGE / 100;
    }

    private boolean initializeAudioRecord() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.");
            return false;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE // Use the calculated buffer size
            );
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord initialization failed");
                audioRecord = null; // Set to null to allow retries
                return false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid AudioRecord parameters", e);
            return false;
        }
    }

    public void startBaselineRecording() {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "startBaselineRecording: Failed to initialize AudioRecord.");
            return;
        }

        isBaselineRecording = true;
        if (microphoneTestTestingCallback != null) {
            microphoneTestTestingCallback.onMicrophoneActive(true);
        }

        new Thread(() -> {
            try {
                audioRecord.startRecording();
                processBaselineNoise();
            } catch (Exception e) {
                Log.e(TAG, "Exception in startBaselineRecording", e);
            } finally {
                stopRecording(); // Use the common stopRecording method
                if (microphoneTestTestingCallback != null) {
                    microphoneTestTestingCallback.onMicrophoneActive(false);
                }
            }
        }).start();
    }


    private void processBaselineNoise() {
        short[] buffer = new short[windowSizeSamples]; // Use windowSizeSamples
        int totalReads = 0;
        double sumNoisePower = 0;

        long startTime = System.currentTimeMillis();
        long recordingDuration = 5000; // 5 seconds

        while (isBaselineRecording && System.currentTimeMillis() - startTime < recordingDuration) {
            int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
            if (shortsRead > 0) {
                // Apply Hanning window
                applyHanningWindow(buffer, shortsRead);
                sumNoisePower += calculatePower(buffer, shortsRead);
                totalReads++;
            }
        }

        if (totalReads > 0) {
            baselineNoisePower = sumNoisePower / totalReads;
            // Determine baseline quality (your original logic, but using calculated baselineNoisePower)
            String qualityLabel;
            int qualityLevel;

            if (baselineNoisePower < 200) {
                qualityLabel = "Excellent";
                qualityLevel = 1;
            } else if (baselineNoisePower < 500) {
                qualityLabel = "Good";
                qualityLevel = 2;
            } else if (baselineNoisePower < 1000) {
                qualityLabel = "Fair";
                qualityLevel = 3;
            } else if (baselineNoisePower < 2000) {
                qualityLabel = "Poor";
                qualityLevel = 4;
            } else {
                qualityLabel = "Very Poor";
                qualityLevel = 5;
            }
            if (microphoneTestTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    microphoneTestTestingCallback.onBaselineQuality(qualityLabel, qualityLevel);
                    microphoneTestTestingCallback.onBaselineRecorded();
                });
            }
        } else {
            Log.e(TAG, "No windows read during baseline recording");
        }
    }


    public void testMicrophone() {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "testMicrophone: Failed to initialize AudioRecord.");
            return;
        }

        isTesting = true;
        if (voiceQualityTestingCallback != null) {
            voiceQualityTestingCallback.onMicrophoneActive(true);
        }

        new Thread(() -> {
            try {
                audioRecord.startRecording();
                processMicrophoneTest();
            } catch (Exception e) {
                Log.e(TAG, "Exception in testMicrophone", e);
            } finally {
                stopRecording(); // Use common stopRecording
                if (voiceQualityTestingCallback != null) {
                    voiceQualityTestingCallback.onMicrophoneActive(false);
                }
            }
        }).start();
    }

    private void processMicrophoneTest() {
        short[] buffer = new short[windowSizeSamples]; // Use window size for buffer
        //removed all totalReads, sumAmplitude, and sumFrequency

        while (isTesting) {
            int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
            if (shortsRead > 0) {
                // Apply Hanning window
                applyHanningWindow(buffer, shortsRead);

                double signalPower = calculatePower(buffer, shortsRead);
                double snr = calculateSNR(signalPower, baselineNoisePower);
                double frequency = estimateFrequency(buffer, shortsRead);


                if (voiceQualityTestingCallback != null) {
                    //removed onTestingDataRecieved
                    //remove onTestCompleted
                    new Handler(Looper.getMainLooper()).post(() -> voiceQualityTestingCallback.onIntermediateSNR(snr)); // Send intermediate SNR
                    //removed the handler
                }
            }
        }
    }

    private void applyHanningWindow(short[] buffer, int validSamples) {
        for (int n = 0; n < validSamples; n++) {
            double multiplier = 0.5 * (1 - Math.cos(2 * Math.PI * n / (validSamples - 1)));
            buffer[n] = (short) (buffer[n] * multiplier);
        }
    }

    private double calculatePower(short[] buffer, int validSamples) {
        double sumOfSquares = 0.0;
        for (int i = 0; i < validSamples; i++) {
            sumOfSquares += (double) buffer[i] * buffer[i];
        }
        return sumOfSquares / validSamples;
    }
    // Improved (but still simple) frequency estimation
    private double estimateFrequency(short[] buffer, int validSamples) {
        if (validSamples == 0) {
            return 0;
        }

        int numSamples = Math.min(validSamples, buffer.length); // Prevent out-of-bounds access
        int peakIndex = 0;
        short maxValue = 0;

        // Find the index of the peak value (highest amplitude)
        for (int i = 0; i < numSamples; i++) {
            if (Math.abs(buffer[i]) > maxValue) {
                maxValue = (short) Math.abs(buffer[i]);
                peakIndex = i;
            }
        }

        // Find the next zero crossing *after* the peak
        int lastZeroCrossingIndex = -1;

        // Start searching for zero crossings after the peak
        for (int i = peakIndex; i < numSamples - 1; i++) {
            if ((buffer[i] > 0 && buffer[i + 1] <= 0) || (buffer[i] < 0 && buffer[i + 1] >= 0)) {
                // Zero crossing found
                if (lastZeroCrossingIndex != -1) {
                    //calculate the distance between zero crossing
                    int samplesBetweenZeroCrossings = i - lastZeroCrossingIndex;
                    //check for div by zero
                    if (samplesBetweenZeroCrossings > 0){
                        //calculate the frequency and return it
                        return SAMPLE_RATE / (double) (2 * samplesBetweenZeroCrossings);
                    }
                }
                lastZeroCrossingIndex = i;
            }
        }

        return 0; // No frequency detected
    }

    private double calculateSNR(double signalPower, double noisePower) {
        if (noisePower == 0) {
            return 100.0; // A large value, indicating very good SNR
        }
        if (signalPower == 0){
            return 0;
        }
        double snr = 10 * Math.log10(signalPower / noisePower);
        // Clamp the SNR to be between 0 and 100 dB
        return Math.max(0.0, Math.min(100.0, snr));
    }


    // Unified stopRecording method
    private void stopRecording() {
        if (audioRecord != null) {
            try {
                if (isBaselineRecording || isTesting) { //check if it is recording
                    audioRecord.stop();
                }
                isTesting = false;
                isBaselineRecording = false;
                Log.d(TAG, "⏺️ Recording stopped.");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping recording", e);
            }
        }
    }

    public void release() {
        stopRecording(); // Ensure recording is stopped before releasing
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    // Callback interfaces (keep your existing ones, but adjust method names)
    public interface VoiceQualityTestingCallback {
        // Remove onTestingDataReceived
        // Remove onTestCompleted
        void onIntermediateSNR(double snr); // Use this for per-window SNR updates
        void onMicrophoneActive(boolean isActive);
    }

    public interface MicrophoneTestTestingCallback {
        void onBaselineRecorded();
        void onBaselineQuality(String qualityLabel, int qualityLevel);
        void onMicrophoneActive(boolean isActive);
    }
}