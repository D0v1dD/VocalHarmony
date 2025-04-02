package com.example.vocalharmony.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // Import Button
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.vocalharmony.R;

import java.util.Locale;
import java.util.Map;

public class AccessDataFragment extends Fragment {

    // Removed graphButton field declaration

    // Keep TextView fields as they might be updated elsewhere or needed for reference
    private TextView textViewLatestScore;
    private TextView textViewS2N;
    private TextView textViewBestScore;
    private TextView textViewBestS2N;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_access_data, container, false);

        // 1) Initialize TextView UI elements
        textViewLatestScore = root.findViewById(R.id.text_latest_score);
        textViewS2N = root.findViewById(R.id.text_s2n);
        textViewBestScore = root.findViewById(R.id.text_best_score);
        textViewBestS2N = root.findViewById(R.id.text_best_s2n);

        // *** FIX: Make graphButton a local variable ***
        Button graphButton = root.findViewById(R.id.button_graph); // Declare locally

        // 2) Load and display data from SharedPreferences
        loadAndDisplayData();

        // 3) Set a click listener to the local graphButton variable
        // Ensure the button and navigation action exist
        if (graphButton != null) { // Add null check for safety
            graphButton.setOnClickListener(v -> {
                try {
                    // If you have a nav action defined from this fragment to graphFragment:
                    Navigation.findNavController(v).navigate(R.id.action_navigation_data_to_graphFragment); // Make sure this action ID is correct in your nav graph
                } catch (IllegalArgumentException e) {
                    // Handle cases where the destination might not be found
                    android.util.Log.e("AccessDataFragment", "Navigation failed: " + e.getMessage());
                    // Optionally show a toast to the user
                    android.widget.Toast.makeText(getContext(), "Cannot navigate to graph.", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            android.util.Log.w("AccessDataFragment", "Graph button not found in layout.");
        }


        return root;
    }

    /**
     * Loads the latest and best SNR values from SharedPreferences and updates the UI.
     */
    private void loadAndDisplayData() {
        // Safety check for context
        if (getContext() == null) {
            android.util.Log.e("AccessDataFragment", "Context is null in loadAndDisplayData.");
            return;
        }

        // 1) Retrieve SharedPreferences
        SharedPreferences sharedPreferences = requireActivity()
                .getSharedPreferences("VocalHarmonyPrefs", Context.MODE_PRIVATE);

        // 2) Retrieve all entries.
        Map<String, ?> allEntries = sharedPreferences.getAll();

        float latestSNR = 0.0f;
        float bestSNR = 0.0f;
        String latestTimestamp = ""; // Initialize to empty string

        // 3) Iterate over all entries
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Check for keys starting with "snr_" and if the value is a Float
            if (key.startsWith("snr_") && value instanceof Float) {
                float snrValue = (Float) value;

                // Extract timestamp (handle potential errors if key format is wrong)
                String timestamp = "";
                if (key.length() > "snr_".length()) {
                    timestamp = key.substring("snr_".length());
                }

                // Compare timestamps lexicographically (assumes YYYY-MM-DD HH:MM:SS format or similar sortable format)
                // Initialize latestTimestamp check ensures first valid entry becomes the latest initially
                if (latestTimestamp.isEmpty() || timestamp.compareTo(latestTimestamp) > 0) {
                    latestTimestamp = timestamp;
                    latestSNR = snrValue;
                }

                // Update bestSNR
                if (snrValue > bestSNR) {
                    bestSNR = snrValue;
                }
            }
        }

        // 4) Display the data in UI
        String latestSNRText = String.format(Locale.getDefault(), "%.1f", latestSNR); // Format to one decimal place like SNR display
        String bestSNRText   = String.format(Locale.getDefault(), "%.1f", bestSNR); // Format to one decimal place

        // Use try-catch when getting strings in case of resource issues during runtime
        try {
            // Set labels directly
            textViewLatestScore.setText(getString(R.string.latest_score_label));
            textViewBestScore.setText(getString(R.string.best_score_label));

            // *** FIX: Use format string for values to avoid concatenation warning ***
            textViewS2N.setText(getString(R.string.s2n_value_format, getString(R.string.s2n_label), latestSNRText));
            textViewBestS2N.setText(getString(R.string.s2n_value_format, getString(R.string.s2n_label), bestSNRText));

        } catch (Exception e) {
            android.util.Log.e("AccessDataFragment", "Error setting text from string resources: " + e.getMessage());
            // Set default text as fallback
            textViewLatestScore.setText("Latest Score:");
            textViewBestScore.setText("Best Score:");
            textViewS2N.setText("SNR: " + latestSNRText);
            textViewBestS2N.setText("SNR: " + bestSNRText);
        }
    }
}