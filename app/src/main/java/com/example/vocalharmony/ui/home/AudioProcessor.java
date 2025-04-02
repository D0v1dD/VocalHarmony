package com.example.vocalharmony.ui.home;

// Imports
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.vocalharmony.R; // Ensure R is imported

public class AudioProcessor {

    private static final String TAG = "AudioProcessor";
    // SharedPreferences Constants
    private static final String PREFS_NAME = "VocalHarmonyPrefs";
    private static final String KEY_BASELINE_POWER = "baselineNoisePower";

    // Audio Configuration Constants
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // Increased buffer size for stability, ensuring it's at least 2048
    private static final int BUFFER_SIZE = Math.max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2,
            2048
    );

    // Window Configuration
    private static final int WINDOW_SIZE_MS = 100; // Window size in milliseconds
    private final int windowSizeSamples; // Calculated in constructor

    // Constants for Baseline Logic
    private static final long BASELINE_RECORDING_DURATION_MS = 5000; // 5 seconds
    private static final double BASELINE_QUALITY_THRESH_EXCELLENT = 200;
    private static final double BASELINE_QUALITY_THRESH_GOOD = 500;
    private static final double BASELINE_QUALITY_THRESH_FAIR = 1000;
    private static final double BASELINE_QUALITY_THRESH_POOR = 2000;
    private static final long THREAD_JOIN_TIMEOUT_MS = 200; // Timeout for stopping thread

    // Internal State Variables
    private AudioRecord audioRecord;
    private volatile boolean isTesting = false;
    private volatile boolean isBaselineRecording = false;
    // Volatile reference to the processing thread. IDE might warn about non-atomic ops,
    // but direct assignment/read-to-local is generally safe for this use case.
    private volatile Thread processingThread = null;
    @NonNull
    private final Context context; // Application context

    // Callbacks - Marked Nullable. IDE might warn checks are always true based on current usage,
    // but null checks ARE necessary because the interface allows nulls.
    @Nullable private final VoiceQualityTestingCallback voiceQualityTestingCallback;
    @Nullable private final MicrophoneTestTestingCallback microphoneTestTestingCallback;

    // Baseline Noise Power - Loaded from Prefs or calculated
    private double baselineNoisePower = 0.0;

    /** Constructor */
    public AudioProcessor(@NonNull Context context,
                          @Nullable VoiceQualityTestingCallback voiceQualityTestingCallback,
                          @Nullable MicrophoneTestTestingCallback microphoneTestTestingCallback) {
        this.context = context.getApplicationContext(); // Use application context
        this.voiceQualityTestingCallback = voiceQualityTestingCallback;
        this.microphoneTestTestingCallback = microphoneTestTestingCallback;
        this.windowSizeSamples = (int) ((double) WINDOW_SIZE_MS / 1000.0 * SAMPLE_RATE);
        if (BUFFER_SIZE <= 0) { Log.e(TAG, "!!! Invalid buffer size calculated: " + BUFFER_SIZE); }
        // Constructor calls loadBaselineFromPrefs for its side effect (setting internal field).
        // IDE might warn that return value is unused here, which is acceptable.
        loadBaselineFromPrefs();
    }

    /** Initializes AudioRecord */
    private boolean initializeAudioRecord() {
        if (isReady()) { Log.d(TAG, "AR already initialized."); return true; }
        if (audioRecord != null) {
            try { Log.w(TAG, "Releasing existing AR instance..."); audioRecord.release(); }
            catch (Exception e) { Log.w(TAG, "Exception releasing old AR in init", e); }
            audioRecord = null;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ RECORD_AUDIO permission not granted."); return false;
        }
        if (BUFFER_SIZE <= 0) { Log.e(TAG, "❌ Invalid buffer size: " + BUFFER_SIZE); return false; }

        int[] audioSources = { MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC };
        for (int source : audioSources) {
            AudioRecord tempAudioRecord = null;
            try {
                Log.i(TAG, "Attempting AR init: Source=" + source + ", Rate=" + SAMPLE_RATE + ", Buffer=" + BUFFER_SIZE);
                tempAudioRecord = new AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
                int state = tempAudioRecord.getState();
                Log.i(TAG, "Attempt result: Source=" + source + ", State=" + state);
                if (state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "✅ AR Initialized successfully with source: " + source);
                    audioRecord = tempAudioRecord; return true;
                } else {
                    Log.w(TAG, "❌ Failed AR init with source: " + source + ", State=" + state);
                    if (tempAudioRecord != null) { tempAudioRecord.release(); }
                }
            } catch (IllegalArgumentException | SecurityException e) {
                Log.w(TAG, "❌ Exception initializing AR source " + source + ": " + e.getMessage());
                // IDE might warn 'tempAudioRecord != null' is always true here, reasoning that if this catch
                // block is reached, the assignment must have succeeded before the exception occurred.
                // Keeping the check as a safety measure.
                if (tempAudioRecord != null) {
                    try { tempAudioRecord.release(); } catch (Exception ex) { Log.e(TAG, "Exception releasing temp AR", ex); }
                }
            }
        }
        Log.e(TAG, "❌ All attempts to initialize AudioRecord failed.");
        audioRecord = null; return false;
    }

    /** Starts baseline recording */
    public void startBaselineRecording() {
        if (isBaselineRecording || isTesting) { Log.w(TAG, "Already running."); return; }
        stopAndReleaseThread();
        // IDE might warn about inverted check. This is intentional error handling.
        if (!initializeAudioRecord()) {
            Log.e(TAG, "❌ Failed init AR. Baseline aborted.");
            // Null checks for callbacks are necessary due to @Nullable annotation.
            if (microphoneTestTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> { if (microphoneTestTestingCallback != null) microphoneTestTestingCallback.onMicrophoneActive(false); });
            } return;
        }
        isBaselineRecording = true; isTesting = false;
        if (microphoneTestTestingCallback != null) {
            new Handler(Looper.getMainLooper()).post(() -> { if (microphoneTestTestingCallback != null) microphoneTestTestingCallback.onMicrophoneActive(true); });
        }
        Log.d(TAG, "🎤 Starting baseline recording THREAD...");
        processingThread = new Thread(() -> {
            try {
                if (!isReady()) { throw new IllegalStateException("AR not ready"); }
                audioRecord.startRecording(); Log.d(TAG, "✅ AR started for baseline.");
                processBaselineNoise();
            } catch (Exception e) { Log.e(TAG, "❌ Exception in baseline thread: " + e.getMessage(), e); }
            finally {
                Log.d(TAG,"Baseline thread finalization..."); stopRecordingInternal(); isBaselineRecording = false;
                if (baselineNoisePower > 0.0) { saveBaselineToPrefs(baselineNoisePower); }
                else { Log.w(TAG, "Baseline not saved (<=0)."); }
                if (microphoneTestTestingCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> { if (microphoneTestTestingCallback != null) microphoneTestTestingCallback.onMicrophoneActive(false); });
                }
                processingThread = null; Log.d(TAG, "✅ Baseline thread finished.");
            }
        });
        processingThread.setName("BaselineRecThread"); processingThread.start();
    }

    /** Processes baseline noise */
    private void processBaselineNoise() {
        short[] buffer = new short[windowSizeSamples]; int totalValidWindowsRead = 0; double sumNoisePower = 0.0; long startTime = System.currentTimeMillis();
        Log.d(TAG, "Starting baseline processing loop for " + BASELINE_RECORDING_DURATION_MS + " ms");
        while (isBaselineRecording && (System.currentTimeMillis() - startTime < BASELINE_RECORDING_DURATION_MS)) {
            if (audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "AR stopped during baseline."); isBaselineRecording = false; break;
            }
            try {
                int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
                if (shortsRead == windowSizeSamples) { applyHanningWindow(buffer, shortsRead); sumNoisePower += calculatePower(buffer, shortsRead); totalValidWindowsRead++; }
                else if (shortsRead > 0) { Log.w(TAG, "Partial baseline read: " + shortsRead); }
                else if (shortsRead < 0) { Log.e(TAG, "Baseline read error: " + shortsRead); isBaselineRecording = false; break; }
            } catch (Exception e) { Log.e(TAG, "Baseline read exception: " + e.getMessage(), e); isBaselineRecording = false; break; }
        } Log.d(TAG, "Baseline loop finished. Windows: " + totalValidWindowsRead);

        if (totalValidWindowsRead > 0) {
            this.baselineNoisePower = sumNoisePower / totalValidWindowsRead; Log.i(TAG, "Baseline power calculated: " + this.baselineNoisePower);
            String qualityLabel; int qualityLevel;
            if (this.baselineNoisePower < BASELINE_QUALITY_THRESH_EXCELLENT) { qualityLabel = context.getString(R.string.mic_quality_excellent); qualityLevel = 1; }
            else if (this.baselineNoisePower < BASELINE_QUALITY_THRESH_GOOD) { qualityLabel = context.getString(R.string.mic_quality_good); qualityLevel = 2; }
            else if (this.baselineNoisePower < BASELINE_QUALITY_THRESH_FAIR) { qualityLabel = context.getString(R.string.mic_quality_moderate); qualityLevel = 3; }
            else if (this.baselineNoisePower < BASELINE_QUALITY_THRESH_POOR) { qualityLabel = context.getString(R.string.mic_quality_poor); qualityLevel = 4; }
            else { qualityLabel = context.getString(R.string.mic_quality_very_poor); qualityLevel = 5; }
            Log.d(TAG, "Baseline quality: " + qualityLabel + " (Level " + qualityLevel + ")");
            if (microphoneTestTestingCallback != null) {
                final String finalQualityLabel = qualityLabel;
                new Handler(Looper.getMainLooper()).post(() -> { if (microphoneTestTestingCallback != null) { microphoneTestTestingCallback.onBaselineQuality(finalQualityLabel, qualityLevel); microphoneTestTestingCallback.onBaselineRecorded(); } });
            }
        } else {
            this.baselineNoisePower = 0.0; Log.w(TAG, "No valid baseline windows.");
            if (microphoneTestTestingCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> { if (microphoneTestTestingCallback != null) { microphoneTestTestingCallback.onBaselineRecorded(); Toast.makeText(context, R.string.baseline_recording_failed, Toast.LENGTH_SHORT).show(); } });
            }
        }
    }

    /** Starts SNR test */
    public void testMicrophone() {
        if (isBaselineRecording || isTesting) { Log.w(TAG, "Already running."); return; }
        if (this.baselineNoisePower <= 0.0) {
            Log.e(TAG, "❌ SNR test needs baseline > 0.");
            if (voiceQualityTestingCallback != null) { new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, R.string.record_baseline_first, Toast.LENGTH_SHORT).show()); }
            return;
        }
        stopAndReleaseThread();
        if (!initializeAudioRecord()) {
            Log.e(TAG, "❌ Failed init AR for SNR test.");
            if (voiceQualityTestingCallback != null) { new Handler(Looper.getMainLooper()).post(() -> { if (voiceQualityTestingCallback != null) voiceQualityTestingCallback.onMicrophoneActive(false); }); }
            return;
        }
        isTesting = true; isBaselineRecording = false;
        if (voiceQualityTestingCallback != null) { new Handler(Looper.getMainLooper()).post(() -> { if (voiceQualityTestingCallback != null) voiceQualityTestingCallback.onMicrophoneActive(true); }); }
        Log.d(TAG, "🎤 Starting SNR test THREAD...");
        processingThread = new Thread(() -> {
            try {
                if (!isReady()) { throw new IllegalStateException("AR not ready"); }
                audioRecord.startRecording(); Log.d(TAG, "✅ AR started for SNR test.");
                processMicrophoneTest();
            } catch (Exception e) { Log.e(TAG, "❌ Exception in SNR thread: " + e.getMessage(), e); }
            finally {
                Log.d(TAG,"SNR thread finalization..."); stopRecordingInternal(); isTesting = false;
                if (voiceQualityTestingCallback != null) { new Handler(Looper.getMainLooper()).post(() -> { if (voiceQualityTestingCallback != null) voiceQualityTestingCallback.onMicrophoneActive(false); }); }
                processingThread = null; Log.d(TAG, "✅ SNR test thread finished.");
            }
        });
        processingThread.setName("SnrTestThread"); processingThread.start();
    }

    /** Processes SNR test data */
    private void processMicrophoneTest() {
        short[] buffer = new short[windowSizeSamples]; Log.d(TAG, "Starting SNR processing loop.");
        while (isTesting) {
            if (audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "AR stopped during SNR."); isTesting = false; break;
            }
            try {
                int shortsRead = audioRecord.read(buffer, 0, windowSizeSamples);
                if (shortsRead == windowSizeSamples) {
                    applyHanningWindow(buffer, shortsRead); double signalPower = calculatePower(buffer, shortsRead); double snr = calculateSNR(signalPower, this.baselineNoisePower);
                    if (voiceQualityTestingCallback != null) { new Handler(Looper.getMainLooper()).post(() -> { if (voiceQualityTestingCallback != null && isTesting) { voiceQualityTestingCallback.onIntermediateSNR(snr); } }); }
                } else if (shortsRead > 0) { Log.v(TAG, "Partial SNR read: " + shortsRead); }
                else if (shortsRead < 0) { Log.e(TAG, "SNR read error: " + shortsRead); isTesting = false; break; }
            } catch (Exception e) { Log.e(TAG, "SNR read exception: " + e.getMessage(), e); isTesting = false; break; }
        } Log.d(TAG, "SNR processing loop finished.");
    }

    /** Public method called by UI to stop active test/recording */
    public void stopTesting() {
        Log.d(TAG, "stopTesting() called externally."); isTesting = false; isBaselineRecording = false;
    }

    /** Internal method to safely stop AudioRecord */
    private void stopRecordingInternal() {
        if (audioRecord != null) {
            try { if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) { Log.d(TAG, "⏹️ Stopping AR internally..."); audioRecord.stop(); Log.d(TAG, "✅ AR stopped internally."); }
            else { Log.d(TAG, "AR not recording."); } }
            catch (IllegalStateException e) { Log.e(TAG, "❌ Error stopping AR internally: " + e.getMessage(), e); }
        } else { Log.d(TAG,"AR null in stopRecordingInternal."); }
        isTesting = false; isBaselineRecording = false; // Reset flags safeguard
    }

    /** Helper to stop and attempt to join the active processing thread */
    private void stopAndReleaseThread() {
        Thread threadToStop = processingThread;
        if (threadToStop != null && threadToStop.isAlive()) {
            Log.d(TAG, "Signaling/joining previous thread (" + threadToStop.getName() + ")...");
            isTesting = false; isBaselineRecording = false; threadToStop.interrupt();
            try { threadToStop.join(THREAD_JOIN_TIMEOUT_MS);
                if (threadToStop.isAlive()) { Log.w(TAG, "⚠️ Thread didn't terminate: "+ threadToStop.getName()); }
                else { Log.d(TAG,"✅ Thread joined: "+ threadToStop.getName()); } }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); Log.e(TAG, "Interrupted joining.", e); }
        } else { Log.d(TAG,"No active thread to stop/join."); }
        processingThread = null;
    }

    // --- Utility and Public Access Methods ---

    /** Gets the current baseline power value */
    public double getBaselineNoisePower() { return this.baselineNoisePower; }

    /**
     * Clears baseline power internally and in SharedPreferences.
     * IDE might warn this is unused *internally*, but it's public API.
     */
    public void clearBaseline() {
        Log.d(TAG,"Clearing baseline..."); this.baselineNoisePower = 0.0;
        try { SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); prefs.edit().remove(KEY_BASELINE_POWER).apply(); Log.i(TAG,"Cleared baseline from Prefs."); }
        catch (Exception e) { Log.e(TAG, "Failed to clear baseline from Prefs", e); }
    }

    // --- SharedPreferences Methods for Baseline ---

    /** Saves baseline power to SharedPreferences */
    private void saveBaselineToPrefs(double calculatedBaselinePower) {
        if (calculatedBaselinePower <= 0.0) { Log.w(TAG, "Not saving non-positive baseline: " + calculatedBaselinePower); return; }
        Log.i(TAG, "Saving baseline power to Prefs: " + calculatedBaselinePower);
        try { SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); prefs.edit().putFloat(KEY_BASELINE_POWER, (float) calculatedBaselinePower).apply(); this.baselineNoisePower = calculatedBaselinePower; }
        catch (Exception e) { Log.e(TAG, "Failed to save baseline to Prefs", e); }
    }

    /** Loads baseline power from SharedPreferences */
    private double loadBaselineFromPrefs() {
        Log.d(TAG, "Loading baseline power from Prefs...");
        try { SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); this.baselineNoisePower = prefs.getFloat(KEY_BASELINE_POWER, 0.0f); Log.i(TAG, "Loaded baseline from Prefs: " + this.baselineNoisePower); return this.baselineNoisePower; }
        catch (Exception e) { Log.e(TAG, "Failed to load baseline from Prefs", e); this.baselineNoisePower = 0.0; return 0.0; }
    }

    // --- State Check and Resource Release ---

    /** Checks if AudioRecord is initialized */
    public boolean isReady() { return audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED; }

    /** Releases AudioRecord resource and cleans up threads */
    public void release() {
        Log.i(TAG, "Releasing AudioProcessor resources..."); stopAndReleaseThread(); isTesting = false; isBaselineRecording = false;
        if (audioRecord != null) {
            AudioRecord recordToRelease = audioRecord; audioRecord = null;
            Log.d(TAG,"Stopping/releasing AR instance...");
            try { if (recordToRelease.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) { recordToRelease.stop(); } recordToRelease.release(); Log.i(TAG,"AR instance released."); }
            catch (Exception e) { Log.e(TAG, "Exception during final AR release: " + e.getMessage(), e); }
        } else { Log.d(TAG,"AR already null during release."); }
        Log.i(TAG,"AudioProcessor release method finished.");
    }

    // --- Calculation Helpers ---

    /** Applies Hanning window */
    private void applyHanningWindow(short[] buffer, int validSamples) {
        if (validSamples <= 1) return;
        for (int n = 0; n < validSamples; n++) { double multiplier = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * n / (validSamples - 1.0))); buffer[n] = (short) (buffer[n] * multiplier); }
    }

    /** Calculates average power */
    private double calculatePower(short[] buffer, int validSamples) {
        if (validSamples <= 0) return 0.0; double sumOfSquares = 0.0;
        for (int i = 0; i < validSamples; i++) { double sample = buffer[i]; sumOfSquares += (sample * sample); }
        return sumOfSquares / validSamples;
    }

    /** Calculates SNR in dB */
    private double calculateSNR(double signalPower, double noisePower) {
        if (noisePower <= 1e-10) { return (signalPower > 1e-10) ? 100.0 : 0.0; }
        if (signalPower <= 1e-10) { return 0.0; }
        double ratio = signalPower / noisePower; if (ratio <= 1.0) { return 0.0; }
        double snr = 10.0 * Math.log10(ratio); return Math.max(0.0, Math.min(100.0, snr));
    }

    // --- Callback Interfaces ---
    public interface VoiceQualityTestingCallback { void onIntermediateSNR(double snr); void onMicrophoneActive(boolean isActive); }
    public interface MicrophoneTestTestingCallback { void onBaselineRecorded(); void onBaselineQuality(@NonNull String qualityLabel, int qualityLevel); void onMicrophoneActive(boolean isActive); }

} // End of AudioProcessor class