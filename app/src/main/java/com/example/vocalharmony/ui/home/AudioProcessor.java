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
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    /**
     * Example bandpass filter for ~60–300 Hz @ 44.1 kHz.
     * Replace with your actual coefficients if you have a properly designed filter.
     */
    private static final double[] BPF_B = {
            0.002608303, 0.0, -0.005216606, 0.0, 0.002608303
    };
    private static final double[] BPF_A = {
            1.0, -3.850372, 5.574444, -3.554681, 0.830301
    };

    // Filter state for Direct Form II
    private final double[] filterState = new double[BPF_A.length - 1];

    private AudioRecord audioRecord;
    private volatile boolean isRecording;
    private double baselineNoisePower = 0;

    private final Context context;
    private final RecordingCallback recordingCallback;

    public AudioProcessor(Context context, RecordingCallback recordingCallback) {
        this.context = context;
        this.recordingCallback = recordingCallback;
    }

    /**
     * Callback interface for main recording process (baseline, SNR, etc.).
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
    // Baseline recording (5 seconds)

    public void recordBaseline() {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "recordBaseline: Failed to initialize AudioRecord.");
            return;
        }
        isRecording = true;

        new Thread(() -> {
            try {
                audioRecord.startRecording();
                processBaseline(5000); // 5-second baseline
            } finally {
                stopAudioRecord();
            }
        }).start();
    }

    private void processBaseline(long baselineDurationMs) {
        short[] buffer = new short[BUFFER_SIZE];
        double[] powerMeasurements = new double[50]; // 50 samples max in 5s (~100ms intervals)
        int measurementsCount = 0;
        long startTime = System.currentTimeMillis();

        Log.d(TAG, "processBaseline: Starting baseline recording for " + baselineDurationMs + "ms...");

        while (isRecording && (System.currentTimeMillis() - startTime) < baselineDurationMs) {
            int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
            if (read < 0) {
                Log.w(TAG, "processBaseline: audioRecord.read returned " + read);
                break;
            }

            if (read > 0) {
                Log.v(TAG, "processBaseline: Read " + read + " samples");

                // Bypass filter for baseline to avoid NaN
                double[] rawData = convertShortToDouble(buffer, read);

                double currentPower = calculatePower(rawData);
                powerMeasurements[measurementsCount % powerMeasurements.length] = currentPower;
                measurementsCount++;
            }
        }

        measurementsCount = Math.min(measurementsCount, powerMeasurements.length);
        if (measurementsCount == 0) {
            baselineNoisePower = 0;
            Log.w(TAG, "No valid baseline measurements recorded. Possibly silent environment or read errors.");
        } else {
            double[] actualData = Arrays.copyOf(powerMeasurements, measurementsCount);
            Arrays.sort(actualData);

            Log.d(TAG, "processBaseline: Collected " + measurementsCount
                    + " power samples. Sorted data=" + Arrays.toString(actualData));

            int midIndex = measurementsCount / 2;
            if (measurementsCount % 2 == 1) {
                baselineNoisePower = actualData[midIndex];
            } else {
                baselineNoisePower = (actualData[midIndex - 1] + actualData[midIndex]) / 2.0;
            }
        }

        Log.d(TAG, "Baseline noise power: " + baselineNoisePower);
        if (recordingCallback != null) {
            recordingCallback.onBaselineRecorded();
        }
    }

    // -----------------------------------------------------------------------
    // Main recording (SNR calculation)

    public void startRecording() {
        if (!initializeAudioRecord()) {
            Log.e(TAG, "startRecording: Failed to initialize AudioRecord.");
            return;
        }
        if (baselineNoisePower <= 0) {
            Log.e(TAG, "No valid baseline. Please record baseline first. baselineNoisePower="
                    + baselineNoisePower);
            return;
        }

        isRecording = true;
        new Thread(() -> {
            try {
                audioRecord.startRecording();
                processRecording();
            } finally {
                stopAudioRecord();
            }
        }).start();
    }

    private void processRecording() {
        short[] buffer = new short[BUFFER_SIZE];
        Log.d(TAG, "processRecording: Starting main recording. Baseline=" + baselineNoisePower);

        while (isRecording) {
            int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
            if (read < 0) {
                Log.w(TAG, "processRecording: audioRecord.read returned " + read + ", stopping.");
                break;
            }
            if (read > 0) {
                // Bypass filter in normal recording to avoid NaN:
                // double[] filtered = applyBandpassFilter(buffer, read);
                double[] rawData = convertShortToDouble(buffer, read);

                // Compute signal power
                double signalPower = calculatePower(rawData);

                // Compute SNR in dB
                double snrDb = 10 * Math.log10(signalPower / baselineNoisePower);

                if (recordingCallback != null) {
                    // Provide raw buffer for potential UI visualization
                    recordingCallback.onAudioDataReceived(Arrays.copyOf(buffer, read));

                    // Provide the computed SNR in dB
                    recordingCallback.onSNRCalculated(snrDb);
                }

                Log.v(TAG, String.format(
                        "SNR=%.2f dB, signalPower=%.6f, baselineNoisePower=%.6f",
                        snrDb, signalPower, baselineNoisePower));
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
                        // Bypass or partially filter
                        // double[] filtered = applyBandpassFilter(buffer, read);
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
    // Utility methods: filter, power, frequency, amplitude

    /**
     * Convert short[] samples to double[] in [-1..1].
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
     * Example placeholder filter. Currently returns 0 for old samples,
     * so it can produce NaN or 0 if used. Bypass recommended unless fixed.
     */
    private double[] applyBandpassFilter(short[] input, int length) {
        if (length <= 0) {
            Log.w(TAG, "applyBandpassFilter: length=" + length + ", returning empty array.");
            return new double[0];
        }

        double[] output = new double[length];

        for (int i = 0; i < length; i++) {
            double sample = input[i] / 32768.0;
            // Direct Form II (incomplete)
            double y = BPF_B[0] * sample + filterState[0];

            // Shift the filter state
            for (int j = 1; j < BPF_B.length; j++) {
                if (j < BPF_A.length) {
                    y += (BPF_B[j] * 0)  // If we had old input samples, we'd use them here
                            - (BPF_A[j] * filterState[j - 1]);
                } else {
                    y += (BPF_B[j] * 0);
                }
            }

            // Shift
            System.arraycopy(filterState, 0, filterState, 1, filterState.length - 1);
            filterState[0] = y;

            output[i] = y;
        }
        return output;
    }

    private double calculatePower(double[] buffer) {
        if (buffer.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double val : buffer) {
            sum += val * val;
        }
        return sum / buffer.length;
    }

    private double calculateAmplitude(double[] buffer) {
        if (buffer.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double val : buffer) {
            sum += Math.abs(val);
        }
        return sum / buffer.length;
    }

    private double calculateDominantFrequency(double[] buffer) {
        if (buffer.length == 0) {
            return 0;
        }

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

    public boolean isBaselineRecorded() {
        return baselineNoisePower > 0;
    }
}
