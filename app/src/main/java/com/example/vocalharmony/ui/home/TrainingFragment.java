package com.example.vocalharmony.ui.home.training;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.vocalharmony.R;

public class TrainingFragment extends Fragment {

    private Button voiceQualityButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_training, container, false);

        // Initialize the Voice Quality button
        voiceQualityButton = rootView.findViewById(R.id.button_voice_quality);

        // Set up the click listener
        voiceQualityButton.setOnClickListener(view -> {
            // Navigate to the VoiceQualityFragment
            Navigation.findNavController(view).navigate(R.id.navigation_voice_quality);
        });

        return rootView;
    }

}
