package com.example.vocalharmony.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import com.example.vocalharmony.ui.home.AudioProcessor;

import com.example.vocalharmony.R;
import com.google.android.material.button.MaterialButton;

public class MicrophoneTestFragment extends Fragment implements AudioProcessor.TestingCallback {

    private static final String TAG = "MicrophoneTestFragment";

    // Placeholder threshold value for amplitude (adjust based on real conditions)
    private static final double SOME_THRESHOLD_VALUE = 0.01;

    private AudioProcessor audioProcessor;
    private TextView testInstructions;
    private MaterialButton buttonStartTest;
    private TextView testFeedback;
    private LinearLayout advancedInfoLayout;
    private TextView amplitudeInfo;
    private TextView frequencyInfo;
    private MaterialButton buttonToggleAdvanced;
    private boolean advancedVisible = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_microphone_test, container, false);

        // Initialize UI elements
        testInstructions = view.findViewById(R.id.test_instructions);
        buttonStartTest = view.findViewById(R.id.button_start_test);
        testFeedback = view.findViewById(R.id.test_feedback);
        advancedInfoLayout = view.findViewById(R.id.advanced_info_layout);
        amplitudeInfo = view.findViewById(R.id.amplitude_info);
        frequencyInfo = view.findViewById(R.id.frequency_info);
        buttonToggleAdvanced = view.findViewById(R.id.button_toggle_advanced);

        // Set up AudioProcessor and callbacks
        audioProcessor = new AudioProcessor(requireContext(), null);

        // Handle "Start Test" button
        buttonStartTest.setOnClickListener(v -> {
            if (audioProcessor != null) {
                audioProcessor.testMicrophone(this);
                testFeedback.setVisibility(View.GONE);
                advancedInfoLayout.setVisibility(View.GONE);
                buttonStartTest.setEnabled(false);
                buttonStartTest.setText("Testing...");
            }
        });

        // Handle "Toggle Advanced Info" button
        buttonToggleAdvanced.setOnClickListener(v -> {
            advancedVisible = !advancedVisible;
            advancedInfoLayout.setVisibility(advancedVisible ? View.VISIBLE : View.GONE);
            buttonToggleAdvanced.setText(advancedVisible ? "Hide Advanced Info" : "Show Advanced Info");
        });

        return view;
    }

    @Override
    public void onTestingDataReceived(short[] audioBuffer) {
        // Optional: Show real-time data to the user or store it for analysis
        // For now, we will log the data size
        Log.d(TAG, "Received audio buffer of size: " + audioBuffer.length);
    }

    @Override
    public void onTestCompleted(double amplitude, double[] frequencySpectrum) {
        // Analyze the amplitude and frequency data
        requireActivity().runOnUiThread(() -> {
            // Re-enable the "Start Test" button
            buttonStartTest.setEnabled(true);
            buttonStartTest.setText("Start Test");

            // Provide user feedback based on amplitude
            if (amplitude < SOME_THRESHOLD_VALUE) {
                testFeedback.setText("Microphone may not be suitable for capturing electrolarynx sounds.");
            } else {
                testFeedback.setText("Microphone seems suitable for capturing electrolarynx sounds.");
            }
            testFeedback.setVisibility(View.VISIBLE);

            // Update advanced info
            amplitudeInfo.setText(String.format("Amplitude: %.2f", amplitude));

            double dominantFrequency = findDominantFrequency(frequencySpectrum);
            frequencyInfo.setText(String.format("Dominant Frequency: %.2f Hz", dominantFrequency));

            if (advancedVisible) {
                advancedInfoLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    // Method to find the dominant frequency from the frequency spectrum
    private double findDominantFrequency(double[] frequencySpectrum) {
        if (frequencySpectrum == null || frequencySpectrum.length == 0) {
            return 0.0;
        }

        double maxAmplitude = Double.NEGATIVE_INFINITY;
        int maxIndex = -1;
        for (int i = 0; i < frequencySpectrum.length; i++) {
            if (frequencySpectrum[i] > maxAmplitude) {
                maxAmplitude = frequencySpectrum[i];
                maxIndex = i;
            }
        }

        // The frequency bins depend on the sample rate and the size of the FFT
        // For the placeholder FFT, let's assume each index corresponds to a frequency bin
        // In a real FFT result, you'd calculate the frequency as (index * sampleRate / FFT_length)
        double freqPerBin = (double) AudioProcessor.SAMPLE_RATE / (frequencySpectrum.length * 2);
        return maxIndex * freqPerBin;
    }
}
