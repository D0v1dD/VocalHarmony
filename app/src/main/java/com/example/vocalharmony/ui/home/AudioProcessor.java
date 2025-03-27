package com.example.vocalharmony.ui.home;

import android.widget.Toast; // <<< Add this line
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
    // Made BUFFER_SIZE final and public if needed elsewhere, or keep private
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // Window Configuration
    private static final int WINDOW_SIZE_MS = 100;
    private final int windowSizeSamples;

    // Internal State
    private AudioRecord audioRecord; // Renamed internally for clarity
    private volatile boolean isTesting = false;
    private volatile boolean isBaselineRecording = false;
    private volatile Thread processingThread = null; // Added: Reference to the active processing thread
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

        // Defensive check for buffer size
        if (BUFFER_SIZE <= 0) {
            Log.e(TAG, "!!! Invalid AudioRecord buffer size calculated: " + BUFFER_SIZE);
            // Consider throwing an exception or setting an error state
        }
    }

    /**
     * Initializes AudioRecord, checking for RECORD_AUDIO permission.
     *
     * @return true if initialization successful, false otherwise.
     */
    private boolean initializeAudioRecord() {
        // Avoid re-initializing if already initialized and valid
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            Log.d(TAG,"AudioRecord already initialized.");
            return true;
        }
        // Release previous instance if it exists but is in a bad state
        if (audioRecord != null) {
            Log.w(TAG,"Releasing previous uninitialized AudioRecord instance.");
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

        try {
            // Use UNPROCESSED if possible (assuming API 24+)
            // If targeting lower, stick with MIC or add build checks
            int audioSource = MediaRecorder.AudioSource.UNPROCESSED; // Or MIC if needed for compatibility
            Log.d(TAG, "Attempting to initialize AudioRecord with source: " + audioSource);

            audioRecord = new AudioRecord(
                    audioSource, // Use UNPROCESSED or MIC
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "✅ AudioRecord successfully initialized.");
                return true;
            } else {
                Log.e(TAG, "❌ AudioRecord initialization failed. State: " + audioRecord.getState());
                if (audioRecord != null) audioRecord.release(); // Clean up failed instance
                audioRecord = null;
                return false;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "❌ Invalid AudioRecord parameters: " + e.getMessage());
            audioRecord = null; // Ensure null on exception
            return false;
        } catch (SecurityException se){
            Log.e(TAG, "❌ SecurityException initializing AudioRecord: " + se.getMessage());
            audioRecord = null;
            return false;
        } catch (UnsupportedOperationException uoe) {
            Log.e(TAG, "❌ Unsupported operation for AudioRecord config (maybe UNPROCESSED source?): " + uoe.getMessage());
            // Consider falling back to MIC if UNPROCESSED fails here
            audioRecord = null;
            return false;
        }
    }

    /**
     * Public method to start recording baseline noise.
     */
    public void startBaselineRecording() {
        if (isBaselineRecording || isTesting) {
            Log.w(TAG, "Already recording or testing, cannot start baseline recording now.");
            return;
        }

        stopAndReleaseThread(); // Ensure previous thread is stopped if any remnants exist

        if (!initializeAudioRecord()) {
            Log.e(TAG, "❌ Failed to initialize AudioRecord. Baseline will NOT be recorded.");
            // Notify callback?
            if (microphoneTestTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        microphoneTestTestingCallback.onMicrophoneActive(false)); // Indicate failure
            }
            return;
        }

        isBaselineRecording = true;
        isTesting = false; // Ensure testing flag is false
        if (microphoneTestTestingCallback != null) {
            // Use Handler to ensure callback is on main thread
            new Handler(Looper.getMainLooper()).post(() ->
                    microphoneTestTestingCallback.onMicrophoneActive(true));
        }

        Log.d(TAG, "🎤 Starting baseline recording THREAD...");
        // Store reference to the new thread
        processingThread = new Thread(() -> {
            try {
                audioRecord.startRecording();
                Log.d(TAG, "✅ AudioRecord started for baseline.");
                processBaselineNoise(); // This loop checks isBaselineRecording flag
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ IllegalStateException starting baseline recording: " + e.getMessage());
                // Update UI about error?
            } catch (Exception e) { // Catch broader exceptions
                Log.e(TAG, "❌ Exception in baseline recording thread: " + e.getMessage());
            } finally {
                // Stop recording ONLY if this thread was the one meant to be recording baseline
                // This check prevents stopping if another operation started quickly after this one was signaled to stop
                if (Thread.currentThread() == processingThread && isBaselineRecording) {
                    stopRecordingInternal(); // Safely stop AudioRecord if needed
                }
                Log.d(TAG, "✅ Baseline recording thread finished.");

                // Ensure flags are false after thread finishes
                isBaselineRecording = false;
                if (microphoneTestTestingCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            microphoneTestTestingCallback.onMicrophoneActive(false));
                }
                processingThread = null; // Clear thread reference after completion
            }
        });
        processingThread.start();
    }

    /**
     * Process baseline noise, computing baselineNoisePower.
     * (Keep existing logic)
     */
    private void processBaselineNoise() {
        // --- KEEP YOUR EXISTING BASELINE PROCESSING LOGIC HERE ---
        // Make sure it respects the 'isBaselineRecording' flag
        short[] buffer = new short[windowSizeSamples];
        int totalReads = 0;
        double sumNoisePower = 0.0;
        long startTime = System.currentTimeMillis();
        long recordingDuration = 5000; // 5 seconds

        while (isBaselineRecording && (System.currentTimeMillis() - startTime < recordingDuration)) {
            if (audioRecord == null) break; // Safety check
            int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
            if (shortsRead > 0) {
                applyHanningWindow(buffer, shortsRead);
                sumNoisePower += calculatePower(buffer, shortsRead);
                totalReads++;
            } else if (shortsRead < 0) {
                Log.e(TAG, "AudioRecord read error during baseline: " + shortsRead);
                break; // Stop on error
            }
        }
        // --- Keep the rest of your baseline calculation and callback logic ---
        if (totalReads > 0) {
            baselineNoisePower = sumNoisePower / totalReads;
            Log.d(TAG, "Baseline noise power: " + baselineNoisePower);
            // Determine quality...
            String qualityLabel; int qualityLevel; // ... your quality logic ...
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
        } else { Log.e(TAG, "❌ No windows read during baseline recording."); }
        // --- End of baseline processing logic ---
    }


    /**
     * Start microphone testing (SNR) after baseline is recorded.
     */
    public void testMicrophone() {
        if (isBaselineRecording || isTesting) {
            Log.w(TAG, "Already recording or testing, cannot start SNR test now.");
            return;
        }
        if (baselineNoisePower <= 0.0) { // Use <= 0 for safety
            Log.e(TAG, "❌ Attempted to start SNR test before recording a valid baseline.");
            // Maybe notify user?
            if (voiceQualityTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Please record baseline first.", Toast.LENGTH_SHORT).show());
            }
            return;
        }

        stopAndReleaseThread(); // Ensure previous thread is stopped

        if (!initializeAudioRecord()) {
            Log.e(TAG, "❌ Failed to initialize AudioRecord. Cannot start SNR test.");
            if (voiceQualityTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        voiceQualityTestingCallback.onMicrophoneActive(false)); // Indicate failure
            }
            return;
        }

        isTesting = true;
        isBaselineRecording = false; // Ensure baseline flag is false
        if (voiceQualityTestingCallback != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    voiceQualityTestingCallback.onMicrophoneActive(true));
        }

        Log.d(TAG, "🎤 Starting microphone test THREAD...");
        processingThread = new Thread(() -> {
            try {
                audioRecord.startRecording();
                Log.d(TAG, "✅ AudioRecord started for microphone test.");
                processMicrophoneTest(); // This loop checks isTesting flag
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ IllegalStateException starting SNR test recording: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "❌ Exception in SNR test thread: " + e.getMessage());
            } finally {
                if (Thread.currentThread() == processingThread && isTesting) {
                    stopRecordingInternal(); // Safely stop AudioRecord if needed
                }
                Log.d(TAG, "✅ Microphone test thread finished.");
                // Ensure flags are false after thread finishes
                isTesting = false;
                if (voiceQualityTestingCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            voiceQualityTestingCallback.onMicrophoneActive(false));
                }
                processingThread = null; // Clear thread reference
            }
        });
        processingThread.start();
    }

    /**
     * Process microphone input for SNR measurement.
     * (Keep existing logic)
     */
    private void processMicrophoneTest() {
        // --- KEEP YOUR EXISTING SNR PROCESSING LOGIC HERE ---
        // Make sure it respects the 'isTesting' flag
        short[] buffer = new short[windowSizeSamples];
        while (isTesting) {
            if (audioRecord == null) break;
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
                break; // Stop on error
            }
        }
        // --- End of SNR processing logic ---
    }

    /**
     * Stop any ongoing test or baseline recording (public method for fragment).
     */
    public void stopTesting() {
        Log.d(TAG, "stopTesting() called. Setting flags to false.");
        isTesting = false;          // Signal testing thread to stop
        isBaselineRecording = false; // Signal baseline thread to stop

        // The running thread (baseline or testing) should detect the flag change,
        // finish its current loop, call stopRecordingInternal() in its finally block,
        // and update the microphoneActive(false) callback.
        // We don't forcefully stop AudioRecord here to allow the thread to finish cleanly.
    }

    /**
     * Internal method to safely stop the AudioRecord session.
     * Should only be called from the processing thread's finally block or release().
     */
    private void stopRecordingInternal() {
        if (audioRecord != null) {
            try {
                // Check state before stopping
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d(TAG, "⏹️ Stopping AudioRecord internally...");
                    audioRecord.stop();
                    Log.d(TAG, "✅ AudioRecord stopped internally.");
                } else {
                    Log.d(TAG,"AudioRecord was not recording, no need to stop.");
                }
            } catch (IllegalStateException e) {
                // This can happen if stop() is called in an invalid state
                Log.e(TAG, "❌ Error stopping AudioRecord internally: " + e.getMessage());
            } catch (Exception e) {
                // Catch any other potential errors during stop
                Log.e(TAG, "❌ Unexpected error stopping AudioRecord: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG,"stopRecordingInternal called but audioRecord is null.");
        }
        // Reset flags just in case, although they should be set by stopTesting() caller
        isTesting = false;
        isBaselineRecording = false;
    }


    // Helper method to stop and join the processing thread if it's running
    private void stopAndReleaseThread() {
        if (processingThread != null && processingThread.isAlive()) {
            Log.d(TAG, "Interrupting and joining previous processing thread...");
            isTesting = false; // Set flags to signal thread
            isBaselineRecording = false;
            processingThread.interrupt(); // Interrupt if it's stuck waiting
            try {
                processingThread.join(200); // Wait a short time for thread to die
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Interrupted while joining processing thread.", e);
            }
            if (processingThread.isAlive()) {
                Log.w(TAG, "Processing thread did not terminate after join.");
            }
            processingThread = null;
        }
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
     * Use this in the fragment instead of checking internal state directly.
     *
     * @return true if AudioRecord is initialized, false otherwise.
     */
    public boolean isReady() {
        return audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED;
    }

    /**
     * Releases the AudioRecord resource and attempts to clean up the processing thread.
     * Call this when the associated fragment/component is destroyed (e.g., in onDestroyView).
     */
    public void release() {
        Log.d(TAG, "Releasing AudioProcessor resources...");
        // 1. Signal any running thread to stop and wait briefly for it
        stopAndReleaseThread();

        // 2. Ensure recording state flags are false
        isTesting = false;
        isBaselineRecording = false;

        // 3. Safely stop (if needed) and release the AudioRecord instance
        if (audioRecord != null) {
            stopRecordingInternal(); // Try to stop first if somehow still recording
            try {
                Log.d(TAG,"Releasing AudioRecord instance...");
                audioRecord.release(); // Release native resources
                Log.d(TAG,"AudioRecord instance released.");
            } catch (Exception e) { // Catch potential errors during final release
                Log.e(TAG, "Exception during final AudioRecord release: " + e.getMessage(), e);
            } finally {
                audioRecord = null; // Nullify the reference
            }
        } else {
            Log.d(TAG,"AudioRecord was already null during release.");
        }
    }


    // --- Calculation Helpers (Keep existing) ---
    private void applyHanningWindow(short[] buffer, int validSamples) {
        for (int n = 0; n < validSamples; n++) {
            double multiplier = 0.5 * (1 - Math.cos(2 * Math.PI * n / (validSamples - 1)));
            buffer[n] = (short) (buffer[n] * multiplier);
        }
    }

    private double calculatePower(short[] buffer, int validSamples) {
        double sumOfSquares = 0.0;
        for (int i = 0; i < validSamples; i++) {
            // Avoid overflow by casting to double *before* squaring
            double sample = buffer[i];
            sumOfSquares += (sample * sample);
        }
        // Prevent division by zero if validSamples is 0
        return (validSamples > 0) ? (sumOfSquares / validSamples) : 0.0;
    }


    private double calculateSNR(double signalPower, double noisePower) {
        if (noisePower <= 0) { // Handle noise being zero or negative
            // Return max SNR or a very large value if signal is present, 0 otherwise
            return (signalPower > 0) ? 100.0 : 0.0;
        }
        double ratio = signalPower / noisePower;
        if (ratio <= 0) { // Handle signal power being zero or less than noise (log undefined/negative)
            return 0.0;
        }
        // Calculate SNR in dB
        double snr = 10 * Math.log10(ratio);
        // Clamp the result to a reasonable range, e.g., [0, 100] dB
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