package com.example.vocalharmony.ui.home;

import android.widget.Toast; // Keep this import
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
    // Calculate buffer size, ensuring it's a multiple of frame size if needed and reasonably large
    private static final int BUFFER_SIZE = Math.max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2, // Often doubled for safety
            2048 // Ensure a reasonable minimum
    );


    // Window Configuration
    private static final int WINDOW_SIZE_MS = 100;
    private final int windowSizeSamples;

    // Internal State
    private AudioRecord audioRecord;
    private volatile boolean isTesting = false;
    private volatile boolean isBaselineRecording = false;
    private volatile Thread processingThread = null;
    private final Context context;

    // Callbacks
    private final VoiceQualityTestingCallback voiceQualityTestingCallback;
    private final MicrophoneTestTestingCallback microphoneTestTestingCallback;

    // Baseline Noise
    private double baselineNoisePower = 0.0;

    /**
     * Constructor
     */
    public AudioProcessor(Context context,
                          VoiceQualityTestingCallback voiceQualityTestingCallback,
                          MicrophoneTestTestingCallback microphoneTestTestingCallback) {
        this.context = context;
        this.voiceQualityTestingCallback = voiceQualityTestingCallback;
        this.microphoneTestTestingCallback = microphoneTestTestingCallback;
        this.windowSizeSamples = (int) ((double) WINDOW_SIZE_MS / 1000 * SAMPLE_RATE);

        if (BUFFER_SIZE <= 0) {
            Log.e(TAG, "!!! Invalid AudioRecord buffer size calculated: " + BUFFER_SIZE);
        }
    }

    /**
     * Initializes AudioRecord, checking for RECORD_AUDIO permission.
     * Tries preferred audio sources and falls back if necessary.
     *
     * @return true if initialization successful, false otherwise.
     */
    private boolean initializeAudioRecord() {
        if (isReady()) { // Use isReady() to check if already initialized
            Log.d(TAG, "AudioRecord already initialized and ready.");
            return true;
        }
        // Release previous instance if it exists but is unusable
        if (audioRecord != null) {
            Log.w(TAG,"Releasing previous unusable AudioRecord instance before re-initializing.");
            audioRecord.release();
            audioRecord = null;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ RECORD_AUDIO permission not granted.");
            return false;
        }

        // Check buffer size again before creation
        if (BUFFER_SIZE <= 0) {
            Log.e(TAG, "❌ Cannot initialize AudioRecord, invalid buffer size: " + BUFFER_SIZE);
            return false;
        }

        // *** UPDATED INITIALIZATION LOGIC WITH FALLBACK ***
        int[] audioSources = {
                MediaRecorder.AudioSource.UNPROCESSED, // Try this first (API 24+)
                MediaRecorder.AudioSource.MIC          // Fallback to default MIC
                // MediaRecorder.AudioSource.VOICE_RECOGNITION // Could be another fallback
        };

        for (int source : audioSources) {
            try {
                Log.d(TAG, "Attempting to initialize AudioRecord with source: " + source);
                // Create new instance for this attempt
                AudioRecord tempAudioRecord = new AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE
                );

                if (tempAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "✅ AudioRecord successfully initialized with source: " + source);
                    audioRecord = tempAudioRecord; // Assign the successful instance
                    return true; // Success!
                } else {
                    Log.w(TAG, "❌ AudioRecord failed to initialize with source: " + source + ". State: " + tempAudioRecord.getState());
                    tempAudioRecord.release(); // Clean up failed instance
                }
            } catch (IllegalArgumentException | SecurityException | UnsupportedOperationException e) {
                Log.w(TAG, "❌ Failed attempt to initialize AudioRecord with source " + source + ": " + e.getMessage());
                // Continue to the next audio source in the loop
            }
        }

        // If loop finishes without returning true, all attempts failed.
        Log.e(TAG, "❌ All attempts to initialize AudioRecord failed.");
        audioRecord = null; // Ensure it's null if all attempts failed
        return false;
        // *** END OF UPDATED INITIALIZATION LOGIC ***
    }


    /**
     * Public method to start recording baseline noise.
     */
    public void startBaselineRecording() {
        if (isBaselineRecording || isTesting) {
            Log.w(TAG, "Already recording or testing, cannot start baseline recording now.");
            return;
        }
        stopAndReleaseThread();
        if (!initializeAudioRecord()) { // Calls the updated method
            Log.e(TAG, "❌ Failed to initialize AudioRecord. Baseline will NOT be recorded.");
            if (microphoneTestTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        microphoneTestTestingCallback.onMicrophoneActive(false));
            }
            return;
        }
        isBaselineRecording = true;
        isTesting = false;
        if (microphoneTestTestingCallback != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    microphoneTestTestingCallback.onMicrophoneActive(true));
        }
        Log.d(TAG, "🎤 Starting baseline recording THREAD...");
        processingThread = new Thread(() -> {
            try {
                // Check audioRecord state before starting
                if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioRecord not initialized before startRecording");
                }
                audioRecord.startRecording();
                Log.d(TAG, "✅ AudioRecord started for baseline.");
                processBaselineNoise();
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ IllegalStateException starting baseline recording: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception in baseline recording thread: " + e.getMessage());
            } finally {
                // Use internal stop which checks state
                stopRecordingInternal();
                Log.d(TAG, "✅ Baseline recording thread finished.");
                isBaselineRecording = false; // Ensure flag is false
                if (microphoneTestTestingCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            microphoneTestTestingCallback.onMicrophoneActive(false));
                }
                processingThread = null;
            }
        });
        processingThread.start();
    }

    /**
     * Process baseline noise, computing baselineNoisePower.
     * (Keep existing logic)
     */
    private void processBaselineNoise() {
        short[] buffer = new short[windowSizeSamples];
        int totalReads = 0;
        double sumNoisePower = 0.0;
        long startTime = System.currentTimeMillis();
        long recordingDuration = 5000; // 5 seconds

        while (isBaselineRecording && (System.currentTimeMillis() - startTime < recordingDuration)) {
            if (audioRecord == null) { Log.w(TAG, "AudioRecord became null during baseline processing."); break; }
            try {
                int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
                if (shortsRead > 0) {
                    applyHanningWindow(buffer, shortsRead);
                    sumNoisePower += calculatePower(buffer, shortsRead);
                    totalReads++;
                } else if (shortsRead < 0) {
                    Log.e(TAG, "AudioRecord read error during baseline: " + shortsRead);
                    break;
                }
            } catch (Exception e) { // Catch potential exceptions during read
                Log.e(TAG, "Exception during AudioRecord.read in baseline: " + e.getMessage(), e);
                isBaselineRecording = false; // Stop processing on error
            }
        }
        // Rest of baseline calculation and callback...
        if (totalReads > 0) {
            baselineNoisePower = sumNoisePower / totalReads;
            Log.d(TAG, "Baseline noise power: " + baselineNoisePower);
            String qualityLabel; int qualityLevel;
            if (baselineNoisePower < 200) { qualityLabel = "Excellent"; qualityLevel = 1; }
            else if (baselineNoisePower < 500) { qualityLabel = "Good"; qualityLevel = 2; }
            else if (baselineNoisePower < 1000) { qualityLabel = "Fair"; qualityLevel = 3; }
            else if (baselineNoisePower < 2000) { qualityLabel = "Poor"; qualityLevel = 4; }
            else { qualityLabel = "Very Poor"; qualityLevel = 5; }
            if (microphoneTestTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    microphoneTestTestingCallback.onBaselineQuality(qualityLabel, qualityLevel);
                    microphoneTestTestingCallback.onBaselineRecorded();
                });
            }
        } else { Log.w(TAG, "No windows read during baseline recording."); } // Changed to warning
    }


    /**
     * Start microphone testing (SNR) after baseline is recorded.
     */
    public void testMicrophone() {
        if (isBaselineRecording || isTesting) {
            Log.w(TAG, "Already recording or testing, cannot start SNR test now.");
            return;
        }
        if (baselineNoisePower <= 0.0) {
            Log.e(TAG, "❌ Attempted to start SNR test before recording a valid baseline.");
            if (voiceQualityTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Please record baseline first.", Toast.LENGTH_SHORT).show());
            }
            return;
        }
        stopAndReleaseThread();
        if (!initializeAudioRecord()) { // Calls the updated method
            Log.e(TAG, "❌ Failed to initialize AudioRecord. Cannot start SNR test.");
            if (voiceQualityTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        voiceQualityTestingCallback.onMicrophoneActive(false));
            }
            return;
        }
        isTesting = true;
        isBaselineRecording = false;
        if (voiceQualityTestingCallback != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    voiceQualityTestingCallback.onMicrophoneActive(true));
        }
        Log.d(TAG, "🎤 Starting microphone test THREAD...");
        processingThread = new Thread(() -> {
            try {
                // Check audioRecord state before starting
                if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioRecord not initialized before startRecording");
                }
                audioRecord.startRecording();
                Log.d(TAG, "✅ AudioRecord started for microphone test.");
                processMicrophoneTest();
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ IllegalStateException starting SNR test recording: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception in SNR test thread: " + e.getMessage());
            } finally {
                stopRecordingInternal();
                Log.d(TAG, "✅ Microphone test thread finished.");
                isTesting = false; // Ensure flag is false
                if (voiceQualityTestingCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            voiceQualityTestingCallback.onMicrophoneActive(false));
                }
                processingThread = null;
            }
        });
        processingThread.start();
    }

    /**
     * Process microphone input for SNR measurement.
     * (Keep existing logic)
     */
    private void processMicrophoneTest() {
        short[] buffer = new short[windowSizeSamples];
        while (isTesting) {
            if (audioRecord == null) { Log.w(TAG, "AudioRecord became null during SNR processing."); break; }
            try {
                int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
                if (shortsRead > 0) {
                    applyHanningWindow(buffer, shortsRead);
                    double signalPower = calculatePower(buffer, shortsRead);
                    double snr = calculateSNR(signalPower, baselineNoisePower);
                    if (voiceQualityTestingCallback != null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                voiceQualityTestingCallback.onIntermediateSNR(snr));
                    }
                } else if (shortsRead < 0) {
                    Log.e(TAG, "AudioRecord read error during SNR test: " + shortsRead);
                    break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during AudioRecord.read in SNR test: " + e.getMessage(), e);
                isTesting = false; // Stop processing on error
            }
        }
    }

    /**
     * Stop any ongoing test or baseline recording (public method for fragment).
     */
    public void stopTesting() {
        Log.d(TAG, "stopTesting() called. Setting flags to false.");
        isTesting = false;
        isBaselineRecording = false;
        // Let the running thread finish its loop and call stopRecordingInternal from its finally block.
    }

    /**
     * Internal method to safely stop the AudioRecord session.
     */
    private void stopRecordingInternal() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d(TAG, "⏹️ Stopping AudioRecord internally...");
                    audioRecord.stop();
                    Log.d(TAG, "✅ AudioRecord stopped internally.");
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Error stopping AudioRecord internally (already stopped?): " + e.getMessage()); // Downgraded to warning
            } catch (Exception e) {
                Log.e(TAG, "❌ Unexpected error stopping AudioRecord: " + e.getMessage(), e);
            }
        }
        // Reset flags here as well for robustness
        isTesting = false;
        isBaselineRecording = false;
    }


    // Helper method to stop and join the processing thread if it's running
    private void stopAndReleaseThread() {
        Thread threadToStop = processingThread; // Capture current thread reference
        if (threadToStop != null && threadToStop.isAlive()) {
            Log.d(TAG, "Signaling and joining previous processing thread...");
            isTesting = false; // Set flags to signal thread
            isBaselineRecording = false;
            threadToStop.interrupt(); // Interrupt if it's blocked (e.g., in read())
            try {
                threadToStop.join(200); // Wait a short time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Interrupted while joining processing thread.", e);
            }
            if (threadToStop.isAlive()) {
                Log.w(TAG, "Processing thread did not terminate after join.");
            }
        }
        processingThread = null; // Ensure reference is cleared
    }


    // --- Utility and Public Access Methods ---

    public double getBaselineNoisePower() {
        return baselineNoisePower;
    }

    public void clearBaseline() {
        baselineNoisePower = 0.0;
        Log.d(TAG, "Baseline noise power cleared.");
    }

    // ---------------------------------------------------------
    //           ADDED: isReady() and release() Methods
    // ---------------------------------------------------------

    /**
     * Checks if the AudioProcessor has successfully initialized the AudioRecord.
     */
    public boolean isReady() {
        return audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED;
    }

    /**
     * Releases the AudioRecord resource and attempts to clean up the processing thread.
     */
    public void release() {
        Log.d(TAG, "Releasing AudioProcessor resources...");
        stopAndReleaseThread(); // Stop thread first
        isTesting = false; // Ensure flags are false
        isBaselineRecording = false;

        if (audioRecord != null) {
            AudioRecord recordToRelease = audioRecord; // Use temp var
            audioRecord = null; // Nullify main reference
            try {
                stopRecordingInternal(); // Call internal stop (checks state) before release
                Log.d(TAG,"Releasing AudioRecord instance...");
                recordToRelease.release();
                Log.d(TAG,"AudioRecord instance released.");
            } catch (Exception e) {
                Log.e(TAG, "Exception during final AudioRecord release: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG,"AudioRecord was already null during release.");
        }
    }


    // --- Calculation Helpers (Keep existing) ---
    private void applyHanningWindow(short[] buffer, int validSamples) { /* ... no change ... */
        for (int n = 0; n < validSamples; n++) {
            double multiplier = 0.5 * (1 - Math.cos(2 * Math.PI * n / (validSamples - 1)));
            buffer[n] = (short) (buffer[n] * multiplier);
        }
    }
    private double calculatePower(short[] buffer, int validSamples) { /* ... no change ... */
        double sumOfSquares = 0.0;
        for (int i = 0; i < validSamples; i++) {
            double sample = buffer[i];
            sumOfSquares += (sample * sample);
        }
        return (validSamples > 0) ? (sumOfSquares / validSamples) : 0.0;
    }
    private double calculateSNR(double signalPower, double noisePower) { /* ... no change ... */
        if (noisePower <= 0) {
            return (signalPower > 0) ? 100.0 : 0.0;
        }
        double ratio = signalPower / noisePower;
        if (ratio <= 0) {
            return 0.0;
        }
        double snr = 10 * Math.log10(ratio);
        return Math.max(0.0, Math.min(100.0, snr));
    }

    // --- Callback Interfaces (Keep existing) ---
    public interface VoiceQualityTestingCallback { /* ... */
        void onIntermediateSNR(double snr);
        void onMicrophoneActive(boolean isActive);
    }
    public interface MicrophoneTestTestingCallback { /* ... */
        void onBaselineRecorded();
        void onBaselineQuality(String qualityLabel, int qualityLevel);
        void onMicrophoneActive(boolean isActive);
    }
}