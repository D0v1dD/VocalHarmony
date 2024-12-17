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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";

    public static final int SAMPLE_RATE = 44100; // Hz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // Adjust this factor if you want to amplify subtle differences
    private static final double SNR_SCALING_FACTOR = 1.0;
    // If you want to apply a rolling average or smoothing to the SNR, consider storing a history
    private static final int SNR_HISTORY_SIZE = 5;

    private AudioRecord audioRecord;
    private boolean isRecording;
    private double[] baselineNoiseValues;
    private double baselineNoisePower = 0;
    private RecordingCallback recordingCallback;
    private TestingCallback testingCallback;
    private Context context;

    // Store recent SNR values for smoothing if needed
    private List<Double> snrHistory = new ArrayList<>();

    public AudioProcessor(Context context, RecordingCallback recordingCallback) {
        this.context = context;
        this.recordingCallback = recordingCallback;
    }

    // Initialize AudioRecord with the appropriate audio source
    private boolean initializeAudioRecord() {
        if (BUFFER_SIZE == AudioRecord.ERROR || BUFFER_SIZE == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: " + BUFFER_SIZE);
            return false;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            int audioSource;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                audioSource = MediaRecorder.AudioSource.UNPROCESSED;
            } else {
                audioSource = MediaRecorder.AudioSource.MIC;
            }

            try {
                audioRecord = new AudioRecord(
                        audioSource,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE
                );

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed. Check the parameters.");
                    audioRecord.release();
                    audioRecord = null;
                    return false;
                }
                return true;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid AudioRecord parameters", e);
                audioRecord = null;
                return false;
            }
        } else {
            Log.e(TAG, "RECORD_AUDIO permission not granted.");
            return false;
        }
    }

    public void recordBaseline() {
        if (!initializeAudioRecord()) {
            return;
        }

        isRecording = true;
        new Thread(this::processBaseline).start();
    }

    private void processBaseline() {
        if (audioRecord != null) {
            audioRecord.startRecording();
            int totalReadSamples = 0;
            int totalDesiredSamples = SAMPLE_RATE * 2; // 2 seconds baseline

            short[] totalAudioBuffer = new short[totalDesiredSamples];

            while (isRecording && totalReadSamples < totalDesiredSamples) {
                short[] audioBuffer = new short[BUFFER_SIZE];
                int readSamples = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);

                if (readSamples > 0) {
                    int samplesToCopy = Math.min(readSamples, totalDesiredSamples - totalReadSamples);
                    System.arraycopy(audioBuffer, 0, totalAudioBuffer, totalReadSamples, samplesToCopy);
                    totalReadSamples += samplesToCopy;
                } else {
                    Log.e(TAG, "Failed to read audio data for baseline.");
                }
            }

            isRecording = false;
            stopAudioRecord();

            // Convert to double[] and normalize
            baselineNoiseValues = new double[totalReadSamples];
            for (int i = 0; i < totalReadSamples; i++) {
                baselineNoiseValues[i] = totalAudioBuffer[i] / 32768.0; // Normalize to [-1,1]
            }

            // (Optional) Apply a simple smoothing on baseline if desired
            // baselineNoiseValues = smoothArray(baselineNoiseValues);

            // Calculate baseline noise power
            baselineNoisePower = calculatePower(baselineNoiseValues);
            Log.d(TAG, "Baseline noise power: " + baselineNoisePower);

            // Notify that baseline recording is complete
            if (recordingCallback != null) {
                recordingCallback.onBaselineRecorded();
            }
        } else {
            Log.e(TAG, "AudioRecord object is null.");
        }
    }

    public void stopBaselineRecording() {
        if (isRecording) {
            isRecording = false;
            stopAudioRecord();
        }
    }

    public void startRecording() {
        if (!initializeAudioRecord()) {
            return;
        }

        if (baselineNoisePower == 0) {
            Log.e(TAG, "Baseline noise power is zero or not set.");
            return;
        }

        isRecording = true;
        new Thread(this::processRecording).start();
    }

    private void processRecording() {
        if (audioRecord != null) {
            audioRecord.startRecording();
            short[] audioBuffer = new short[BUFFER_SIZE];

            while (isRecording) {
                int readSamples = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);
                if (readSamples > 0) {
                    short[] trimmedBuffer = Arrays.copyOf(audioBuffer, readSamples);

                    // Apply optional band-pass filter or noise reduction here if needed
                    // trimmedBuffer = applyBandPassFilter(trimmedBuffer);

                    // Convert and calculate SNR
                    if (recordingCallback != null) {
                        recordingCallback.onAudioDataReceived(trimmedBuffer);
                        double snrValue = calculateSNR(trimmedBuffer);

                        // If you want to smooth the SNR values, uncomment the next line
                        // snrValue = smoothSNRValue(snrValue);

                        recordingCallback.onSNRCalculated(snrValue);
                    }
                } else {
                    Log.e(TAG, "Failed to read audio data.");
                }
            }
            stopAudioRecord();
        } else {
            Log.e(TAG, "AudioRecord object is null.");
        }
    }

    public void stopRecording() {
        if (isRecording) {
            isRecording = false;
            stopAudioRecord();
        }
    }

    public boolean isBaselineRecorded() {
        return baselineNoisePower > 0;
    }

    public void testMicrophone(TestingCallback testingCallback) {
        if (!initializeAudioRecord()) {
            return;
        }

        this.testingCallback = testingCallback;
        isRecording = true;

        new Thread(() -> {
            if (audioRecord == null) {
                Log.e(TAG, "AudioRecord not properly initialized for testing");
                return;
            }

            audioRecord.startRecording();
            int totalReadSamples = 0;
            int totalDesiredSamples = SAMPLE_RATE * 3; // 3 seconds for testing
            short[] totalAudioBuffer = new short[totalDesiredSamples];

            while (isRecording && totalReadSamples < totalDesiredSamples) {
                short[] audioBuffer = new short[BUFFER_SIZE];
                int readSamples = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);

                if (readSamples > 0) {
                    int samplesToCopy = Math.min(readSamples, totalDesiredSamples - totalReadSamples);
                    System.arraycopy(audioBuffer, 0, totalAudioBuffer, totalReadSamples, samplesToCopy);
                    totalReadSamples += samplesToCopy;

                    // Callback for real-time testing data
                    if (testingCallback != null) {
                        short[] trimmedBuffer = Arrays.copyOf(audioBuffer, readSamples);
                        testingCallback.onTestingDataReceived(trimmedBuffer);
                    }
                } else {
                    Log.e(TAG, "Failed to read audio data.");
                }
            }
            isRecording = false;
            stopAudioRecord();

            // Analyze audio after recording
            double amplitude = calculateAmplitude(totalAudioBuffer);
            double[] frequencySpectrum = calculateFrequencySpectrum(totalAudioBuffer);

            if (testingCallback != null) {
                testingCallback.onTestCompleted(amplitude, frequencySpectrum);
            }

        }).start();
    }

    public void stopMicrophoneTest() {
        if (isRecording) {
            isRecording = false;
            stopAudioRecord();
        }
    }

    // Utility Methods
    private void stopAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            } finally {
                audioRecord.release();
                audioRecord = null;
            }
        }
    }

    private double calculateSNR(short[] signalBuffer) {
        double[] signal = new double[signalBuffer.length];
        for (int i = 0; i < signalBuffer.length; i++) {
            signal[i] = signalBuffer[i] / 32768.0; // Normalize to [-1,1]
        }

        // (Optional) Apply amplitude scaling to highlight small differences
        // for (int i = 0; i < signal.length; i++) {
        //     signal[i] *= SNR_SCALING_FACTOR;
        // }

        double signalPower = calculatePower(signal);

        if (baselineNoisePower == 0) {
            Log.e(TAG, "Baseline noise power is zero, cannot calculate SNR");
            return 0;
        }

        double snr = 10 * Math.log10((signalPower / baselineNoisePower)) * SNR_SCALING_FACTOR;

        Log.d(TAG, "Signal Power: " + signalPower);
        Log.d(TAG, "Baseline Noise Power: " + baselineNoisePower);
        Log.d(TAG, "Calculated SNR: " + snr);

        return snr;
    }

    private double calculatePower(double[] buffer) {
        double sum = 0;
        for (double value : buffer) {
            sum += value * value;
        }
        return sum / buffer.length;
    }

    private double calculateAmplitude(short[] audioBuffer) {
        long sum = 0;
        for (short sample : audioBuffer) {
            sum += Math.abs(sample);
        }
        return (double) sum / audioBuffer.length;
    }

    private double[] calculateFrequencySpectrum(short[] audioBuffer) {
        double[] audioDataDouble = new double[audioBuffer.length];
        for (int i = 0; i < audioBuffer.length; i++) {
            audioDataDouble[i] = audioBuffer[i] / 32768.0; // Normalize
        }

        double[] frequencySpectrum = new double[audioDataDouble.length / 2];

        // TODO: Implement FFT and fill frequencySpectrum array
        // Placeholder: Consider using a third-party FFT library
        // or Android's native FFT if available.

        return frequencySpectrum;
    }

    // (Optional) Smooth the SNR values if needed
    // private double smoothSNRValue(double snrValue) {
    //     snrHistory.add(snrValue);
    //     if (snrHistory.size() > SNR_HISTORY_SIZE) {
    //         snrHistory.remove(0);
    //     }
    //     double avg = 0;
    //     for (double val : snrHistory) {
    //         avg += val;
    //     }
    //     return avg / snrHistory.size();
    // }

    // (Optional) Apply a custom band-pass filter to the audio to isolate electrolarynx frequencies
    // private short[] applyBandPassFilter(short[] audioBuffer) {
    //     // TODO: Implement a band-pass filter that focuses on the known frequency range of the electrolarynx
    //     // This will help highlight speech frequencies and reduce environmental noise.
    //     return audioBuffer;
    // }

    // Define the RecordingCallback interface
    public interface RecordingCallback {
        void onAudioDataReceived(short[] audioBuffer);
        void onBaselineRecorded();
        void onSNRCalculated(double snrValue);
    }

    // TestingCallback interface for microphone testing
    public interface TestingCallback {
        void onTestingDataReceived(short[] audioBuffer);
        void onTestCompleted(double amplitude, double[] frequencySpectrum);
    }
}
