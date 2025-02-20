package com.example.vocalharmony.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.vocalharmony.R;

import java.util.Locale;
import java.util.Map;

public class AccessDataFragment extends Fragment {

    private TextView textViewLatestScore;  // "Latest Score" label
    private TextView textViewS2N;         // Latest SNR
    private TextView textViewBestScore;   // "Best Score" label
    private TextView textViewBestS2N;     // Best SNR
    private Button graphButton;           // Button to navigate to GraphFragment (future)

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_access_data, container, false);

        // 1) Initialize UI elements
        textViewLatestScore = root.findViewById(R.id.text_latest_score);
        textViewS2N = root.findViewById(R.id.text_s2n);
        textViewBestScore = root.findViewById(R.id.text_best_score);
        textViewBestS2N = root.findViewById(R.id.text_best_s2n);
        graphButton = root.findViewById(R.id.button_graph);

        // 2) Load and display data from SharedPreferences
        loadAndDisplayData();

        // 3) Set a click listener to the graphButton to navigate
        graphButton.setOnClickListener(v -> {
            // If you have a nav action defined from navigation_data to graphFragment:
            Navigation.findNavController(v).navigate(R.id.action_navigation_data_to_graphFragment);
        });

        return root;
    }

    /**
     * Loads the latest and best SNR values from SharedPreferences and updates the UI.
     */
    private void loadAndDisplayData() {
        // 1) Retrieve SharedPreferences
        SharedPreferences sharedPreferences = requireActivity()
                .getSharedPreferences("VocalHarmonyPrefs", Context.MODE_PRIVATE);

        // 2) Retrieve all entries. Keys should be in the form "snr_<timestamp>"
        Map<String, ?> allEntries = sharedPreferences.getAll();

        float latestSNR = 0.0f;  // Will store the most recently recorded SNR
        float bestSNR = 0.0f;    // Will store the highest SNR
        String latestTimestamp = "";

        // 3) Iterate over all entries
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // We only care about keys like "snr_2025-03-01 15:35:00"
            if (key.startsWith("snr_") && value instanceof Float) {
                float snrValue = (Float) value;

                // Extract timestamp from the key by removing the prefix "snr_"
                String timestamp = key.substring("snr_".length());

                // A simple string compare can track the "latest" timestamp if your format is consistent
                // e.g. "2025-03-01 15:35:00" is lexicographically comparable if zero-padded
                if (latestTimestamp.compareTo(timestamp) < 0) {
                    latestTimestamp = timestamp;
                    latestSNR = snrValue;
                }

                // Always update bestSNR with the maximum
                if (snrValue > bestSNR) {
                    bestSNR = snrValue;
                }
            }
        }

        // 4) Display the data in UI
        //    (If no data was found, defaults remain 0.0)
        //    For localization, format the float with 2 decimals
        String latestSNRText = String.format(Locale.getDefault(), "%.2f", latestSNR);
        String bestSNRText   = String.format(Locale.getDefault(), "%.2f", bestSNR);

        // "Latest Score" and "SNR"
        textViewLatestScore.setText(getString(R.string.latest_score_label));
        textViewS2N.setText(getString(R.string.s2n_label) + " " + latestSNRText);

        // "Best Score" and "SNR"
        textViewBestScore.setText(getString(R.string.best_score_label));
        textViewBestS2N.setText(getString(R.string.s2n_label) + " " + bestSNRText);
    }
}
