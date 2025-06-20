package com.example.vocalharmony.ui.home; // Or your actual package

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.vocalharmony.R;
// If this import line still shows an error, please ensure your ConsentFragment.java file
// is located at 'com/example/vocalharmony/ui/dashboard/ConsentFragment.java' and has no errors.
import com.example.vocalharmony.ui.dashboard.ConsentFragment;

public class TrainingFragment extends Fragment {

    private static final String TAG = "TrainingFragment";
    private Button voiceQualityButton;
    private Button wordEnunciationButton;
    private Button sentenceRecordingButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_training, container, false);

        voiceQualityButton = rootView.findViewById(R.id.button_voice_quality);
        wordEnunciationButton = rootView.findViewById(R.id.button_word_enunciation);
        sentenceRecordingButton = rootView.findViewById(R.id.button_sentence_recording);

        // --- Listener for Voice Quality Button ---
        voiceQualityButton.setOnClickListener(view -> {
            Log.d(TAG, "Voice Quality button clicked. Navigating to VoiceQualityFragment...");
            try {
                // Make sure this action ID is correct in your navigation graph
                Navigation.findNavController(view).navigate(R.id.action_trainingFragment_to_voiceQualityFragment);
            } catch (Exception e) {
                Log.e(TAG, "Navigation to VoiceQualityFragment failed: " + e.getMessage());
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Could not open Voice Quality screen.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // --- MODIFIED: Listener for Word Enunciation Button ---
        // This button now navigates to the Speech-To-Text test fragment.
        wordEnunciationButton.setOnClickListener(view -> {
            Log.d(TAG, "Word Enunciation button clicked. Navigating to Speech-to-Text test screen...");
            try {
                // Use the action ID we created in mobile_navigation.xml
                Navigation.findNavController(view).navigate(R.id.action_trainingFragment_to_speechToTextFragment);
            } catch (Exception e) {
                Log.e(TAG, "Navigation to SpeechToTextFragment failed", e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Error opening feature.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // --- Listener for Sentence Recording Button (handles consent) ---
        sentenceRecordingButton.setOnClickListener(view -> {
            Log.d(TAG, "Sentence Recording button clicked. Checking consent...");
            if (getContext() == null || !isAdded()) {
                Log.e(TAG, "Cannot check consent, context is null or fragment not added.");
                return;
            }

            if (ConsentFragment.hasUserConsented(requireContext())) {
                // Already consented, navigate directly to SentenceRecordingFragment
                Log.d(TAG, "Consent already given. Navigating to recording feature.");
                try {
                    Navigation.findNavController(view).navigate(R.id.action_trainingFragment_to_sentenceRecordingFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Navigation to SentenceRecordingFragment failed", e);
                    Toast.makeText(getContext(), "Error opening recording feature.", Toast.LENGTH_SHORT).show();
                }
            } else if (!ConsentFragment.isConsentStatusSet(requireContext())) {
                // Consent not set yet, navigate to ConsentFragment
                Log.d(TAG, "Consent not set. Navigating to ConsentFragment.");
                try {
                    Navigation.findNavController(view).navigate(R.id.action_trainingFragment_to_consentFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Navigation to ConsentFragment failed", e);
                    Toast.makeText(getContext(), "Error opening consent screen.", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Consent status IS set, but it was 'false' (user disagreed previously)
                Log.d(TAG, "User previously disagreed consent.");
                Toast.makeText(requireContext(), R.string.consent_feature_disabled, Toast.LENGTH_LONG).show();
            }
        });

        Log.d(TAG, "TrainingFragment UI initialized.");
        return rootView;
    }

}