package com.example.vocalharmony.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";

    // Audio settings
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    /**
     * Example bandpass filter for ~60–300 Hz @ 44.1 kHz.
     * Replace these with your actual coefficients if available.
     */
    private static final double[] BPF_B = { 0.002608303, 0.0, -0.005216606, 0.0, 0.002608303 };
    private static final double[] BPF_A = { 1.0, -3.850372, 5.574444, -3.554681, 0.830301 };

    // Filter state for Direct Form II (not fully implemented here)
    private final double[] filterState = new double[BPF_A.length - 1];

    private AudioRecord audioRecord;
    private volatile boolean isRecording;

    // Baseline noise powers
    private double environmentBaselineNoisePower = 0;
    private double deviceBaselineNoisePower = 0;

    // Flags to indicate whether a baseline has been recorded
    private boolean environmentBaselineRecorded = false;
    private boolean deviceBaselineRecorded = false;

    // Which baseline mode to use when computing SNR ("environment" or "device")
    private String baselineMode = "environment"; // default

    // Gain factor for device baseline recordings (adjust as needed)
    private static final double DEVICE_GAIN_FACTOR = 10.0;

    private final Context context;
    private final RecordingCallback recordingCallback;

    public AudioProcessor(Context context, RecordingCallback recordingCallback) {
        this.context = context;
        this.recordingCallback = recordingCallback;
    }

    /**
     * Callback interface for the main recording process (baseline, SNR, etc.).
     */
    public interface RecordingCallback {
        void onAudioDataReceived(short[] audioBuffer);
        void onBaselineRecorded();
        void onSNRCalculated(double snrValue);
    }

    /**
     * Optional interface for a short microphone test (e.g., 3-sec check).
     */
    public interface TestingCallback {
        void onTestingDataReceived(short[] audioBuffer);
        void onTestCompleted(double amplitude, double dominantFrequency);
    }

    // -----------------------------------------------------------------------
    // Initialization and release

    private boolean initializeAudioRecord() {
        if (BUFFER_SIZE <= 0) {
            Log.e(TAG, "Invalid buffer size: " + BUFFER_SIZE);
            return false;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.");
            return false;
        }
        int audioSource = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                ? MediaRecorder.AudioSource.UNPROCESSED
                : MediaRecorder.AudioSource.MIC;
        try {
            audioRecord = new AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                releaseAudioRecord();
                return false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid AudioRecord parameters", e);
            releaseAudioRecord();
            return false;
        }
    }

    private void releaseAudioRecord() {
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void stopAudioRecord() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            } finally {
                releaseAudioRecord();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Baseline recording

    /**
     * Record the environment baseline (e.g., 5000 ms).
     */
    public void recordEnvironmentBaseline() {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "recordEnvironmentBaseline: Failed to initialize AudioRecord.");
            return;
        }
        isRecording = true;
        new Thread(() -> {
            processBaseline(5000, "environment");
            stopAudioRecord();
        }).start();
    }

    /**
     * Record the device hum baseline (e.g., 3000 ms).
     */
    public void recordDeviceHumBaseline() {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "recordDeviceHumBaseline: Failed to initialize AudioRecord.");
            return;
        }
        isRecording = true;
        new Thread(() -> {
            processBaseline(3000, "device");
            stopAudioRecord();
        }).start();
    }

    /**
     * Process baseline measurement for the given duration and type.
     *
     * @param baselineDurationMs Duration in milliseconds.
     * @param type               "environment" or "device"
     */
    private void processBaseline(long baselineDurationMs, String type) {
        short[] buffer = new short[BUFFER_SIZE];
        double[] powerMeasurements = new double[50];
        int measurementsCount = 0;
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "processBaseline: Starting " + type + " baseline recording for " + baselineDurationMs + "ms...");
        while (isRecording && (System.currentTimeMillis() - startTime) < baselineDurationMs) {
            int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
            Log.d(TAG, "audioRecord.read returned: " + read);
            if (read < 0) {
                Log.w(TAG, "processBaseline: audioRecord.read returned " + read);
                break;
            }
            if (read > 0) {
                // Log a few sample values for debugging purposes
                StringBuilder sampleLog = new StringBuilder();
                for (int i = 0; i < Math.min(read, 5); i++) {
                    sampleLog.append(buffer[i]).append(" ");
                }
                Log.d(TAG, "Raw sample values: " + sampleLog.toString());
                double[] rawData = convertShortToDouble(buffer, read);
                if ("device".equals(type)) {
                    // Apply gain for device baseline recordings
                    for (int i = 0; i < rawData.length; i++) {
                        rawData[i] *= DEVICE_GAIN_FACTOR;
                    }
                }
                double currentPower = calculatePower(rawData);
                Log.d(TAG, "Current power for " + type + " baseline: " + currentPower);
                powerMeasurements[measurementsCount % powerMeasurements.length] = currentPower;
                measurementsCount++;
            }
        }
        measurementsCount = Math.min(measurementsCount, powerMeasurements.length);
        double baselinePower = 0;
        if (measurementsCount == 0) {
            baselinePower = 0;
            Log.w(TAG, "No valid baseline measurements recorded for " + type + ".");
        } else {
            double[] actualData = Arrays.copyOf(powerMeasurements, measurementsCount);
            Arrays.sort(actualData);
            int midIndex = measurementsCount / 2;
            if (measurementsCount % 2 == 1) {
                baselinePower = actualData[midIndex];
            } else {
                baselinePower = (actualData[midIndex - 1] + actualData[midIndex]) / 2.0;
            }
        }
        Log.d(TAG, type + " baseline noise power: " + baselinePower);
        if ("environment".equals(type)) {
            environmentBaselineNoisePower = baselinePower;
            environmentBaselineRecorded = true;
        } else if ("device".equals(type)) {
            deviceBaselineNoisePower = baselinePower;
            deviceBaselineRecorded = true;
        }
        if (recordingCallback != null) {
            recordingCallback.onBaselineRecorded();
        }
    }

    // -----------------------------------------------------------------------
    // Main recording (SNR calculation)

    /**
     * Set the baseline mode to use when computing SNR.
     *
     * @param mode "device" or "environment"
     */
    public void setBaselineMode(String mode) {
        if ("device".equalsIgnoreCase(mode) || "environment".equalsIgnoreCase(mode)) {
            baselineMode = mode.toLowerCase();
            Log.d(TAG, "Baseline mode set to: " + baselineMode);
        }
    }

    /**
     * Get the current baseline mode.
     *
     * @return The baseline mode ("device" or "environment").
     */
    public String getBaselineMode() {
        return baselineMode;
    }

    /**
     * Start main audio recording using the current baseline.
     */
    public void startRecording() {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "startRecording: Failed to initialize AudioRecord.");
            return;
        }
        double selectedBaseline = baselineMode.equals("device")
                ? deviceBaselineNoisePower
                : environmentBaselineNoisePower;
        if (selectedBaseline <= 0) {
            Log.e(TAG, "No valid " + baselineMode + " baseline recorded. Baseline noise power=" + selectedBaseline);
            return;
        }
        isRecording = true;
        new Thread(() -> {
            try {
                audioRecord.startRecording();
                processRecording(selectedBaseline);
            } finally {
                stopAudioRecord();
            }
        }).start();
    }

    private void processRecording(double baselineNoise) {
        short[] buffer = new short[BUFFER_SIZE];
        Log.d(TAG, "processRecording: Starting main recording. Baseline (" + baselineMode + ")=" + baselineNoise);
        while (isRecording) {
            int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
            if (read < 0) {
                Log.w(TAG, "processRecording: audioRecord.read returned " + read + ", stopping.");
                break;
            }
            if (read > 0) {
                double[] rawData = convertShortToDouble(buffer, read);
                double signalPower = calculatePower(rawData);
                double snrDb = 10 * Math.log10(signalPower / baselineNoise);
                if (recordingCallback != null) {
                    recordingCallback.onAudioDataReceived(Arrays.copyOf(buffer, read));
                    recordingCallback.onSNRCalculated(snrDb);
                }
                Log.v(TAG, String.format("SNR=%.2f dB, signalPower=%.6f, baselineNoisePower=%.6f",
                        snrDb, signalPower, baselineNoise));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Microphone test (3-second amplitude/frequency check)

    public void testMicrophone(TestingCallback callback) {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "testMicrophone: Failed to initialize AudioRecord.");
            return;
        }
        isRecording = true;
        new Thread(() -> {
            try {
                audioRecord.startRecording();
                short[] buffer = new short[BUFFER_SIZE];
                double maxAmplitude = 0.0;
                double dominantFreq = 0.0;
                long startTime = System.currentTimeMillis();
                while (isRecording && (System.currentTimeMillis() - startTime) < 3000) {
                    int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
                    if (read < 0) {
                        Log.w(TAG, "testMicrophone: read returned " + read);
                        break;
                    }
                    if (read > 0) {
                        double[] rawData = convertShortToDouble(buffer, read);
                        double amplitude = calculateAmplitude(rawData);
                        double freq = calculateDominantFrequency(rawData);
                        if (callback != null) {
                            callback.onTestingDataReceived(Arrays.copyOf(buffer, read));
                        }
                        if (amplitude > maxAmplitude) {
                            maxAmplitude = amplitude;
                            dominantFreq = freq;
                        }
                    }
                }
                if (callback != null) {
                    callback.onTestCompleted(maxAmplitude, dominantFreq);
                }
            } finally {
                stopAudioRecord();
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Utility methods

    /**
     * Convert an array of short samples to an array of doubles in the range [-1, 1].
     */
    private double[] convertShortToDouble(short[] input, int length) {
        if (length <= 0) {
            return new double[0];
        }
        double[] output = new double[length];
        for (int i = 0; i < length; i++) {
            output[i] = input[i] / 32768.0;
        }
        return output;
    }

    /**
     * Placeholder for a bandpass filter using Direct Form II.
     * Currently bypasses filtering by ignoring past sample history.
     */
    private double[] applyBandpassFilter(short[] input, int length) {
        if (length <= 0) {
            Log.w(TAG, "applyBandpassFilter: length=" + length + ", returning empty array.");
            return new double[0];
        }
        double[] output = new double[length];
        for (int i = 0; i < length; i++) {
            double sample = input[i] / 32768.0;
            double y = BPF_B[0] * sample + filterState[0];
            for (int j = 1; j < BPF_B.length; j++) {
                if (j < BPF_A.length) {
                    y += (BPF_B[j] * 0) - (BPF_A[j] * filterState[j - 1]);
                } else {
                    y += (BPF_B[j] * 0);
                }
            }
            System.arraycopy(filterState, 0, filterState, 1, filterState.length - 1);
            filterState[0] = y;
            output[i] = y;
        }
        return output;
    }

    private double calculatePower(double[] buffer) {
        if (buffer.length == 0) return 0;
        double sum = 0;
        for (double val : buffer) {
            sum += val * val;
        }
        return sum / buffer.length;
    }

    private double calculateAmplitude(double[] buffer) {
        if (buffer.length == 0) return 0;
        double sum = 0;
        for (double val : buffer) {
            sum += Math.abs(val);
        }
        return sum / buffer.length;
    }

    private double calculateDominantFrequency(double[] buffer) {
        if (buffer.length == 0) return 0;
        int fftSize = 1024;
        double[] fftBuffer = new double[fftSize * 2];
        System.arraycopy(buffer, 0, fftBuffer, 0, Math.min(buffer.length, fftSize));
        DoubleFFT_1D fft = new DoubleFFT_1D(fftSize);
        fft.realForward(fftBuffer);
        double maxMagnitude = 0.0;
        int maxIndex = 0;
        for (int i = 0; i < fftSize / 2; i++) {
            double re = fftBuffer[2 * i];
            double im = fftBuffer[2 * i + 1];
            double magnitude = Math.sqrt(re * re + im * im);
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude;
                maxIndex = i;
            }
        }
        return (maxIndex * SAMPLE_RATE) / (double) fftSize;
    }

    // -----------------------------------------------------------------------
    // Additional Public Methods

    public void stopBaselineRecording() {
        stopAudioRecord();
    }

    public void stopRecording() {
        stopAudioRecord();
    }

    public boolean isEnvironmentBaselineRecorded() {
        return environmentBaselineRecorded;
    }

    public boolean isDeviceHumBaselineRecorded() {
        return deviceBaselineRecorded;
    }
}
