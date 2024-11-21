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


import java.util.Arrays;


public class AudioProcessor {
    private static final String TAG = "AudioProcessor";


    public static final int SAMPLE_RATE = 44100; // Hz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);


    private AudioRecord audioRecord;
    private boolean isRecording;
    private double[] baselineNoiseValues;
    private double baselineNoisePower = 0;
    private RecordingCallback recordingCallback;
    private TestingCallback testingCallback;
    private Context context;


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
            int totalDesiredSamples = SAMPLE_RATE * 2; // Record for 2 seconds
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


                    if (recordingCallback != null) {
                        recordingCallback.onAudioDataReceived(trimmedBuffer);


                        // Calculate SNR
                        double snrValue = calculateSNR(trimmedBuffer);
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


    // *** Added testMicrophone Method ***
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
            int totalDesiredSamples = SAMPLE_RATE * 3; // Record for 3 seconds
            short[] totalAudioBuffer = new short[totalDesiredSamples];


            while (isRecording && totalReadSamples < totalDesiredSamples) {
                short[] audioBuffer = new short[BUFFER_SIZE];
                int readSamples = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);


                if (readSamples > 0) {
                    int samplesToCopy = Math.min(readSamples, totalDesiredSamples - totalReadSamples);
                    System.arraycopy(audioBuffer, 0, totalAudioBuffer, totalReadSamples, samplesToCopy);
                    totalReadSamples += samplesToCopy;


                    // Callback to notify new audio data
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


        double signalPower = calculatePower(signal);


        if (baselineNoisePower == 0) {
            Log.e(TAG, "Baseline noise power is zero, cannot calculate SNR");
            return 0;
        }


        double snr = 10 * Math.log10(signalPower / baselineNoisePower);


        // Log values for debugging
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
        // Convert short[] to double[] for FFT
        double[] audioDataDouble = new double[audioBuffer.length];
        for (int i = 0; i < audioBuffer.length; i++) {
            audioDataDouble[i] = audioBuffer[i] / 32768.0; // Normalize
        }


        // Implement FFT or use a library
        // Placeholder for FFT calculation
        double[] frequencySpectrum = new double[audioDataDouble.length / 2];


        // TODO: Implement FFT and fill frequencySpectrum array


        return frequencySpectrum;
    }


    // Define the RecordingCallback interface
    public interface RecordingCallback {
        void onAudioDataReceived(short[] audioBuffer);
        void onBaselineRecorded();
        void onSNRCalculated(double snrValue);
    }


    // Define the TestingCallback interface
    public interface TestingCallback {
        void onTestingDataReceived(short[] audioBuffer);
        void onTestCompleted(double amplitude, double[] frequencySpectrum);
    }
}
