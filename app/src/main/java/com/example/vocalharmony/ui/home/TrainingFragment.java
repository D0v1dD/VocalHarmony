package com.example.vocalharmony.ui.home; // Or .ui

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
import androidx.navigation.NavDirections; // Optional: Import if using Safe Args plugin
import androidx.navigation.Navigation;

import com.example.vocalharmony.R;

// ** THIS VERSION ONLY HANDLES NAVIGATION **
public class TrainingFragment extends Fragment {

    private static final String TAG = "TrainingFragment";
    private Button voiceQualityButton;
    private Button wordEnunciationButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate the simplified layout (R.layout.fragment_training with only two buttons)
        View rootView = inflater.inflate(R.layout.fragment_training, container, false);

        voiceQualityButton = rootView.findViewById(R.id.button_voice_quality);
        wordEnunciationButton = rootView.findViewById(R.id.button_word_enunciation);

        // Set up click listener to NAVIGATE to VoiceQualityFragment
        voiceQualityButton.setOnClickListener(view -> {
            Log.d(TAG, "Voice Quality button clicked. Navigating to VoiceQualityFragment...");
            try {
                // ** Use the correct Action ID from your res/navigation/mobile_navigation.xml **
                Navigation.findNavController(view).navigate(R.id.action_trainingFragment_to_voiceQualityFragment); // <-- REPLACE WITH YOUR ACTION ID

            } catch (Exception e) {
                Log.e(TAG, "Navigation to VoiceQualityFragment failed: " + e.getMessage());
                Toast.makeText(getContext(), "Could not open Voice Quality screen.", Toast.LENGTH_SHORT).show();
            }
        });

        // Placeholder listener for the other button
        wordEnunciationButton.setOnClickListener(view -> {
            Log.d(TAG, "Word Enunciation button clicked.");
            Toast.makeText(getContext(), "Word Enunciation feature not yet implemented.", Toast.LENGTH_SHORT).show();
            // TODO: Implement later
        });

        Log.d(TAG, "TrainingFragment UI initialized (Navigation Setup).");
        return rootView;
    }

    // No other AudioProcessor or SNR logic belongs here
}