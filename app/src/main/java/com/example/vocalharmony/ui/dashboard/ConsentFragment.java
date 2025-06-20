package com.example.vocalharmony.ui.dashboard; // Adjust package if needed

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html; // For rendering basic HTML in consent text
import android.text.Spanned; // For rendering basic HTML
import android.text.method.LinkMovementMethod; // Make links clickable if any
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocalharmony.R; // Your R file
import com.google.android.material.button.MaterialButton;

public class ConsentFragment extends Fragment {

    private static final String TAG = "ConsentFragment";
    // Key for storing consent status in SharedPreferences
    public static final String PREFS_NAME = "VocalHarmonyPrefs";
    public static final String PREF_KEY_CONSENT_GIVEN = "user_consent_data_collection_given";
    public static final String PREF_KEY_CONSENT_STATUS_SET = "user_consent_status_set"; // To know if user made a choice


    private TextView consentInfoTextView;
    private MaterialButton agreeButton;
    private MaterialButton disagreeButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_consent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        consentInfoTextView = view.findViewById(R.id.consent_info_textview);
        agreeButton = view.findViewById(R.id.agree_button);
        disagreeButton = view.findViewById(R.id.disagree_button);

        // Load and display formatted consent text
        String consentHtml = getString(R.string.consent_details_placeholder);
        Spanned formattedText;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            formattedText = Html.fromHtml(consentHtml, Html.FROM_HTML_MODE_LEGACY);
        } else {
            //noinspection deprecation
            formattedText = Html.fromHtml(consentHtml);
        }
        consentInfoTextView.setText(formattedText);
        consentInfoTextView.setMovementMethod(LinkMovementMethod.getInstance()); // Make links clickable

        // Set button listeners
        agreeButton.setOnClickListener(v -> handleConsent(true));
        disagreeButton.setOnClickListener(v -> handleConsent(false));
    }

    private void handleConsent(boolean agreed) {
        Log.d(TAG, "Consent given: " + agreed);
        if (getContext() == null) return;

        // Save consent status to SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_KEY_CONSENT_GIVEN, agreed);
        editor.putBoolean(PREF_KEY_CONSENT_STATUS_SET, true); // Mark that user made a choice
        editor.apply(); // Apply changes asynchronously

        if (agreed) {
            // Navigate to the Sentence Recording Fragment
            navigateToSentenceRecording();
        } else {
            // Navigate back or show message and disable feature access
            Toast.makeText(getContext(), R.string.consent_feature_disabled, Toast.LENGTH_LONG).show();
            // Navigate back (pop this fragment off the stack)
            NavHostFragment.findNavController(this).popBackStack();
            // Or navigate to a specific 'home' or 'settings' destination if appropriate
            // NavHostFragment.findNavController(this).navigate(R.id.action_consentFragment_to_homeFragment);
        }
    }

    private void navigateToSentenceRecording() {
        try {
            NavController navController = NavHostFragment.findNavController(this);
            // IMPORTANT: Replace 'R.id.action_consentFragment_to_sentenceRecordingFragment'
            // with the actual ID of the navigation action defined in your navigation graph
            // that goes FROM ConsentFragment TO SentenceRecordingFragment.
            navController.navigate(R.id.action_consentFragment_to_sentenceRecordingFragment);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Handle navigation errors (e.g., NavController not found, action ID incorrect)
            Log.e(TAG, "Navigation failed after consent agreement.", e);
            if(isAdded()) Toast.makeText(getContext(), "Error navigating to recording feature.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Static helper method to check consent status from other fragments/activities ---
    public static boolean hasUserConsented(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Return true only if the user explicitly agreed (status is set AND it's true)
        return prefs.getBoolean(PREF_KEY_CONSENT_STATUS_SET, false) &&
                prefs.getBoolean(PREF_KEY_CONSENT_GIVEN, false);
    }
    public static boolean isConsentStatusSet(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_KEY_CONSENT_STATUS_SET, false);
    }
}