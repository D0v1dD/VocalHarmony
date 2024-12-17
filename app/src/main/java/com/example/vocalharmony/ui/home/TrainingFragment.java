package com.example.vocalharmony.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.vocalharmony.R;

public class TrainingFragment extends Fragment {

    private static final String TAG = "TrainingFragment";
    private Button voiceQualityButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_training, container, false);

        // Initialize UI components
        initializeUIComponents(rootView);

        return rootView;
    }

    private void initializeUIComponents(View rootView) {
        voiceQualityButton = rootView.findViewById(R.id.button_voice_quality);

        // Set up the click listener on the Voice Quality button
        voiceQualityButton.setOnClickListener(view -> {
            Log.d(TAG, "Voice Quality button clicked. Navigating to VoiceQualityFragment...");
            // Navigate to the VoiceQualityFragment
            Navigation.findNavController(view).navigate(R.id.navigation_voice_quality);
        });

        Log.d(TAG, "TrainingFragment UI components initialized.");
    }

    @Override
    public void onResume() {
        super.onResume();
        // If any conditions are needed before enabling the Voice Quality button, add them here
        voiceQualityButton.setEnabled(true);
        Log.d(TAG, "TrainingFragment resumed and Voice Quality button enabled.");
    }
}
