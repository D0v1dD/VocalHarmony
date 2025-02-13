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

    // Audio Configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // Window Configuration
    private static final int WINDOW_SIZE_MS = 100;
    private final int windowSizeSamples;

    // Internal State
    private AudioRecord audioRecord;
    private volatile boolean isTesting = false;
    private volatile boolean isBaselineRecording = false;
    private final Context context;

    // Callbacks
    private final VoiceQualityTestingCallback voiceQualityTestingCallback;
    private final MicrophoneTestTestingCallback microphoneTestTestingCallback;

    // Baseline Noise
    private double baselineNoisePower = 0.0;

    /**
     * Constructor
     *
     * @param context                      application or activity context
     * @param voiceQualityTestingCallback  callback for voice quality (SNR) updates
     * @param microphoneTestTestingCallback callback for microphone test updates
     */
    public AudioProcessor(Context context,
                          VoiceQualityTestingCallback voiceQualityTestingCallback,
                          MicrophoneTestTestingCallback microphoneTestTestingCallback) {
        this.context = context;
        this.voiceQualityTestingCallback = voiceQualityTestingCallback;
        this.microphoneTestTestingCallback = microphoneTestTestingCallback;

        // Calculate number of samples in each processing window
        this.windowSizeSamples = (int) ((double) WINDOW_SIZE_MS / 1000 * SAMPLE_RATE);
    }

    /**
     * Initializes AudioRecord, checking for RECORD_AUDIO permission.
     *
     * @return true if initialization successful, false otherwise.
     */
    private boolean initializeAudioRecord() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ RECORD_AUDIO permission not granted.");
            return false;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "✅ AudioRecord successfully initialized.");
                return true;
            } else {
                Log.e(TAG, "❌ AudioRecord initialization failed.");
                audioRecord = null;
                return false;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "❌ Invalid AudioRecord parameters: " + e.getMessage());
            return false;
        }
    }

    /**
     * Public method to start recording baseline noise.
     */
    public void startBaselineRecording() {
        // Ensure any previous test is fully stopped
        stopTesting();

        if (!initializeAudioRecord()) {
            Log.e(TAG, "❌ Failed to initialize AudioRecord. Baseline will NOT be recorded.");
            return;
        }

        isBaselineRecording = true;
        if (microphoneTestTestingCallback != null) {
            microphoneTestTestingCallback.onMicrophoneActive(true);
        }

        Log.d(TAG, "🎤 Starting baseline recording THREAD...");
        new Thread(() -> {
            try {
                audioRecord.startRecording();
                Log.d(TAG, "✅ AudioRecord started for baseline.");
                processBaselineNoise();
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception in startBaselineRecording: " + e.getMessage());
            } finally {
                stopRecording(); // Safely stop
                Log.d(TAG, "✅ Baseline recording thread finished.");

                if (microphoneTestTestingCallback != null) {
                    microphoneTestTestingCallback.onMicrophoneActive(false);
                }
            }
        }).start();
    }

    /**
     * Process baseline noise, computing baselineNoisePower.
     */
    private void processBaselineNoise() {
        short[] buffer = new short[windowSizeSamples];
        int totalReads = 0;
        double sumNoisePower = 0.0;

        long startTime = System.currentTimeMillis();
        long recordingDuration = 5000; // 5 seconds

        while (isBaselineRecording && System.currentTimeMillis() - startTime < recordingDuration) {
            int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
            if (shortsRead > 0) {
                applyHanningWindow(buffer, shortsRead);
                sumNoisePower += calculatePower(buffer, shortsRead);
                totalReads++;
            }
        }

        if (totalReads > 0) {
            baselineNoisePower = sumNoisePower / totalReads;
            Log.d(TAG, "Baseline noise power: " + baselineNoisePower);

            // Determine baseline quality
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

            // Notify fragment on main thread
            if (microphoneTestTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    microphoneTestTestingCallback.onBaselineQuality(qualityLabel, qualityLevel);
                    microphoneTestTestingCallback.onBaselineRecorded();
                });
            }
        } else {
            Log.e(TAG, "❌ No windows read during baseline recording.");
        }
    }

    /**
     * Start microphone testing (SNR) after baseline is recorded.
     */
    public void testMicrophone() {
        if (baselineNoisePower == 0.0) {
            Log.e(TAG, "❌ Attempted to start SNR test before recording baseline.");
            return;
        }

        if (!initializeAudioRecord()) {
            Log.e(TAG, "❌ Failed to initialize AudioRecord. Cannot start SNR test.");
            return;
        }

        isTesting = true;
        if (voiceQualityTestingCallback != null) {
            voiceQualityTestingCallback.onMicrophoneActive(true);
        }

        Log.d(TAG, "🎤 Starting microphone test THREAD...");
        new Thread(() -> {
            try {
                audioRecord.startRecording();
                Log.d(TAG, "✅ AudioRecord started for microphone test.");
                processMicrophoneTest();
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception in testMicrophone: " + e.getMessage());
            } finally {
                stopRecording();
                Log.d(TAG, "✅ Microphone test thread finished.");

                if (voiceQualityTestingCallback != null) {
                    voiceQualityTestingCallback.onMicrophoneActive(false);
                }
            }
        }).start();
    }

    /**
     * Process microphone input for SNR measurement.
     */
    private void processMicrophoneTest() {
        short[] buffer = new short[windowSizeSamples];

        while (isTesting) {
            int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
            if (shortsRead > 0) {
                applyHanningWindow(buffer, shortsRead);

                double signalPower = calculatePower(buffer, shortsRead);
                double snr = calculateSNR(signalPower, baselineNoisePower);

                // Post intermediate SNR updates
                if (voiceQualityTestingCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            voiceQualityTestingCallback.onIntermediateSNR(snr));
                }
            }
        }
    }

    /**
     * Stop any ongoing test (public method for fragment).
     */
    public void stopTesting() {
        isTesting = false;
        stopRecording();
    }

    /**
     * Safely stop the AudioRecord session.
     */
    private void stopRecording() {
        if (audioRecord != null) {
            try {
                // Only stop if actually recording
                if (isBaselineRecording || isTesting) {
                    Log.d(TAG, "⏹️ Stopping AudioRecord...");
                    audioRecord.stop();
                    Log.d(TAG, "✅ AudioRecord successfully stopped.");
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ Error stopping recording: " + e.getMessage());
            }
        }
        isBaselineRecording = false;
        isTesting = false;
        Log.d(TAG, "⏺️ Recording state flags reset.");
    }

    /**
     * @return baselineNoisePower to check if baseline is recorded (>0)
     */
    public double getBaselineNoisePower() {
        return baselineNoisePower;
    }

    /**
     * Clears any previously recorded baseline.
     */
    public void clearBaseline() {
        baselineNoisePower = 0.0;
    }

    // ---------------------------------------------------------
    //           Missing Methods: applyHanningWindow, etc.
    // ---------------------------------------------------------

    /**
     * Apply a Hanning window to reduce spectral leakage.
     */
    private void applyHanningWindow(short[] buffer, int validSamples) {
        for (int n = 0; n < validSamples; n++) {
            double multiplier = 0.5 * (1 - Math.cos(2 * Math.PI * n / (validSamples - 1)));
            buffer[n] = (short) (buffer[n] * multiplier);
        }
    }

    /**
     * Calculate the average power of the given samples.
     */
    private double calculatePower(short[] buffer, int validSamples) {
        double sumOfSquares = 0.0;
        for (int i = 0; i < validSamples; i++) {
            sumOfSquares += ((double) buffer[i] * buffer[i]);
        }
        return sumOfSquares / validSamples;
    }

    /**
     * Calculate SNR in dB, clamped to [0,100].
     */
    private double calculateSNR(double signalPower, double noisePower) {
        if (noisePower == 0) {
            return 100.0;
        }
        if (signalPower == 0) {
            return 0.0;
        }
        double snr = 10 * Math.log10(signalPower / noisePower);
        return Math.max(0.0, Math.min(100.0, snr));
    }

    // ---------------------------------------------------------
    //           Callback Interfaces
    // ---------------------------------------------------------

    /**
     * Interface for fragments that handle real-time voice quality (SNR) updates.
     */
    public interface VoiceQualityTestingCallback {
        /**
         * Called with intermediate SNR data during microphone testing.
         */
        void onIntermediateSNR(double snr);

        /**
         * Called when microphone state changes (active/inactive).
         */
        void onMicrophoneActive(boolean isActive);
    }

    /**
     * Interface for fragments that handle baseline recording updates.
     */
    public interface MicrophoneTestTestingCallback {
        /**
         * Called once the baseline is fully recorded.
         */
        void onBaselineRecorded();

        /**
         * Called with the baseline quality label and level.
         */
        void onBaselineQuality(String qualityLabel, int qualityLevel);

        /**
         * Called when microphone state changes (active/inactive).
         */
        void onMicrophoneActive(boolean isActive);
    }
}
