package com.example.vocalharmony.ui.home;

import android.Manifest;
import android.content.Context;
// Added for requireContext() check
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.google.android.material.button.MaterialButton;

// Locale import removed as String.format doesn't strictly need it here
// import java.util.Locale;

public class MicrophoneTestFragment extends Fragment implements AudioProcessor.MicrophoneTestTestingCallback {

    private static final String TAG = "MicrophoneTestFragment";

    // --- UI Elements ---
    private MaterialButton buttonRecordBaseline;
    private TextView testFeedback;
    private TextView baselineQualityLabel;
    private TextView baselineQualityLevel;

    // --- State & Logic Variables ---
    private AudioProcessor audioProcessor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private boolean isRecordingBaseline = false; // Added to track recording state locally

    // --- Fragment Lifecycle & Setup ---
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Log.d(TAG, "Permission result received: " + isGranted);
                    if (isGranted) {
                        Log.d(TAG, "✅ Permission granted via launcher.");
                        initializeAudioProcessor(); // Initialize processor now that permission is granted
                        updateButtonState(); // Update button based on new state
                        updateFeedback(getString(R.string.mic_permission_granted_message));
                    } else {
                        // Use requireContext() safely within the callback if needed for Toast
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_LONG).show();
                        }
                        Log.w(TAG, "❌ Permission denied via launcher."); // Use warning log level
                        updateButtonState(); // Ensure button is disabled
                        updateFeedback(getString(R.string.mic_permission_denied_message));
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_microphone_test, container, false);
        initializeViews(view);
        setupButtonListeners();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        resetUIState(); // Reset UI first
        checkMicrophonePermission(); // Check permission and potentially init processor
    }

    // --- UI Initialization & State ---
    private void initializeViews(View view) {
        testFeedback = view.findViewById(R.id.test_feedback);
        buttonRecordBaseline = view.findViewById(R.id.button_record_baseline);
        baselineQualityLabel = view.findViewById(R.id.baseline_quality_label);
        baselineQualityLevel = view.findViewById(R.id.baseline_quality_level);
        // Ensure all views were found
        if (testFeedback == null || buttonRecordBaseline == null || baselineQualityLabel == null || baselineQualityLevel == null) {
            Log.e(TAG, "Error: One or more UI components not found in layout!");
            // Consider showing an error message to the user
        }
        Log.d(TAG, "MicrophoneTestFragment views initialized.");
    }

    private void resetUIState() {
        Log.d(TAG, "Resetting UI state.");
        isRecordingBaseline = false; // Ensure state flag is reset
        if (baselineQualityLabel != null) baselineQualityLabel.setText(R.string.baseline_quality_label);
        if (baselineQualityLevel != null) {
            baselineQualityLevel.setText(""); // Clear previous quality text
            // Keep visibility as is, let onBaselineQuality handle making it visible
            // baselineQualityLevel.setVisibility(View.VISIBLE); // Or set to GONE/INVISIBLE initially
        }
        updateFeedback(getString(R.string.baseline_initial_instructions));
        updateButtonState(); // Centralized method to set button text/enabled state
    }

    private void initializeAudioProcessor() {
        // Only initialize if permission is granted and instance doesn't exist
        if (audioProcessor == null && hasAudioPermission() && getContext() != null) {
            Log.i(TAG, "🔧 Initializing AudioProcessor instance...");
            try {
                // Create instance, passing 'this' for MicrophoneTest callbacks
                audioProcessor = new AudioProcessor(requireContext(), null, this);

                // *** REMOVED the premature isReady() check ***
                // We only care if the object was created successfully.
                // Readiness check happens in recordBaseline() before starting.

                Log.i(TAG, "✅ AudioProcessor instance created successfully.");
                // No need to update feedback here unless constructor throws error

            } catch (Exception e){ // Catch potential errors during constructor
                Log.e(TAG, "❌ Exception during AudioProcessor creation: " + e.getMessage(), e);
                updateFeedback(getString(R.string.audio_processor_error));
                audioProcessor = null; // Ensure null on failure
                updateButtonState(); // Ensure button state reflects processor failure
            }
        } else if (audioProcessor != null) {
            Log.d(TAG,"AudioProcessor instance already exists.");
        } else if (!hasAudioPermission()) {
            Log.w(TAG,"AudioProcessor initialization skipped: Permission not granted.");
        } else {
            Log.e(TAG, "AudioProcessor initialization skipped: Context is null.");
        }
    }

    private void setupButtonListeners() {
        if (buttonRecordBaseline != null) {
            buttonRecordBaseline.setOnClickListener(v -> recordBaseline());
            Log.d(TAG,"OnClickListener set for Record Baseline button.");
        } else {
            Log.e(TAG, "Record Baseline button is null, cannot set listener!");
        }
    }

    // Centralized method to update button text and enabled state
    private void updateButtonState() {
        if (!isAdded() || buttonRecordBaseline == null) return; // Check fragment state and button existence

        boolean hasPerm = hasAudioPermission();
        boolean canRecord = hasPerm && !isRecordingBaseline; // Can record if permission granted AND not already recording

        buttonRecordBaseline.setEnabled(canRecord);

        if (isRecordingBaseline) {
            buttonRecordBaseline.setText(R.string.recording_baseline_button_text);
        } else {
            // Check if baseline exists using AudioProcessor's value
            // Requires AudioProcessor instance to exist
            boolean baselineExists = (audioProcessor != null && audioProcessor.getBaselineNoisePower() > 0.0);
            if (baselineExists) {
                buttonRecordBaseline.setText(R.string.baseline_recorded); // Show "Recorded (Tap to Redo)"
            } else {
                buttonRecordBaseline.setText(R.string.record_baseline); // Show "Record Baseline"
            }
        }
        Log.d(TAG, "Button state updated: Enabled=" + canRecord + ", Text=" + buttonRecordBaseline.getText());
    }


    // --- Actions ---
    private void recordBaseline() {
        Log.d(TAG, "Record Baseline button pressed.");
        // 1. Check Permission
        if (!hasAudioPermission()) {
            Log.w(TAG, "Record Baseline pressed, but permission missing. Requesting...");
            requestAudioPermission(); // Request permission
            return; // Stop processing until permission result returns
        }

        // 2. Check/Initialize AudioProcessor (ensure instance exists)
        if (audioProcessor == null) {
            Log.w(TAG,"AudioProcessor is null. Attempting initialization...");
            initializeAudioProcessor();
            // Check again after attempting init
            if (audioProcessor == null) {
                Log.e(TAG, "❌ AudioProcessor initialization failed! Cannot start baseline.");
                updateFeedback(getString(R.string.audio_processor_error));
                updateButtonState(); // Ensure button is disabled
                return;
            }
        }

        // 3. *** Check AudioProcessor Readiness (NOW is the correct time) ***
        // This checks if the underlying AudioRecord can be initialized.
        // Note: initializeAudioRecord is PRIVATE in AudioProcessor, called by startBaselineRecording
        // We rely on startBaselineRecording to perform the actual initialization attempt.
        // If startBaselineRecording fails initialization, it should handle logging/callbacks.

        // 4. Check if already recording
        if (isRecordingBaseline) {
            Log.w(TAG, "Record Baseline pressed, but already recording.");
            return; // Ignore if already in progress
        }


        Log.i(TAG, "🎤 Starting baseline recording process...");
        isRecordingBaseline = true; // Set state flag
        updateButtonState(); // Update button text/state to "Recording..."
        updateFeedback(getString(R.string.baseline_recording_message));

        // 5. Call AudioProcessor method
        audioProcessor.startBaselineRecording(); // This method now handles internal initialization
    }

    // --- Permission Handling ---
    private void checkMicrophonePermission() {
        if (!hasAudioPermission()) {
            Log.w(TAG, "Permission check: Not granted.");
            updateFeedback(getString(R.string.mic_permission_needed_message));
        } else {
            Log.d(TAG, "Permission check: Already granted.");
            initializeAudioProcessor(); // Initialize processor if permission exists
        }
        updateButtonState(); // Update button based on permission status
    }

    private boolean hasAudioPermission() {
        if (getContext() == null) {
            Log.e(TAG, "hasAudioPermission check failed: Context is null.");
            return false;
        }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        Log.d(TAG,"Requesting microphone permission via launcher.");
        // Consider showing rationale before launching if needed
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    // --- AudioProcessor.MicrophoneTestTestingCallback Implementation ---

    @Override
    public void onBaselineRecorded() {
        // Ensure updates run on the main thread
        mainHandler.post(() -> {
            // Check fragment validity
            if (!isAdded() || getContext() == null) {
                Log.w(TAG,"onBaselineRecorded callback received, but fragment not valid.");
                return;
            }
            Log.i(TAG, "✅ Callback: Baseline recording process completed.");
            isRecordingBaseline = false; // Update state flag
            updateButtonState(); // Update button text to "Recorded (Tap to Redo)" and enable it
            updateFeedback(getString(R.string.baseline_success_message)); // Show success message
            Toast.makeText(requireContext(), R.string.baseline_recorded_toast, Toast.LENGTH_SHORT).show();

            // Baseline value is saved internally by AudioProcessor, no action needed here.
            Log.d(TAG, "Baseline value should have been saved internally by AudioProcessor.");
        });
    }

    @Override
    public void onBaselineQuality(@NonNull String qualityLabel, int qualityLevel) {
        // Ensure updates run on the main thread
        mainHandler.post(() -> {
            // Check fragment validity
            if (!isAdded() || getContext() == null) {
                Log.w(TAG,"onBaselineQuality callback received, but fragment not valid.");
                return;
            }
            // Ensure views are still valid
            if (baselineQualityLabel == null || baselineQualityLevel == null) {
                Log.e(TAG, "onBaselineQuality callback received, but UI views are null.");
                return;
            }

            // Format the quality string (e.g., "Good (Level 2)")
            String qualityText = getString(R.string.baseline_quality_display_format, qualityLabel, qualityLevel);

            // Update the TextViews
            baselineQualityLabel.setText(R.string.baseline_quality_label); // Set static label text
            baselineQualityLevel.setText(qualityText); // Set dynamic quality text
            baselineQualityLevel.setVisibility(View.VISIBLE); // Make sure it's visible

            Log.i(TAG, "Callback: Baseline Quality updated - " + qualityText);
        });
    }

    @Override
    public void onMicrophoneActive(boolean isActive) {
        // Ensure updates run on the main thread
        mainHandler.post(() -> {
            // Check fragment validity
            if (!isAdded()) {
                Log.w(TAG,"onMicrophoneActive callback received, but fragment not valid.");
                return;
            }
            Log.d(TAG, isActive ? "🎤 Callback: Mic became active" : "🎤 Callback: Mic became inactive");

            // Update recording state flag if mic becomes inactive unexpectedly
            if (!isActive && isRecordingBaseline) {
                Log.w(TAG, "Mic became inactive while baseline recording was thought to be active.");
                isRecordingBaseline = false;
                // Update feedback only if the button *still* says "Recording..."
                if (buttonRecordBaseline != null &&
                        buttonRecordBaseline.getText().toString().equals(getString(R.string.recording_baseline_button_text))) {
                    updateFeedback(getString(R.string.baseline_stopped_message)); // Or a specific error message
                }
            }
            // Update button enabled state based on mic activity and permission
            updateButtonState();
        });
    }

    // --- Helper Methods ---
    private void updateFeedback(String message) {
        // Check fragment and view validity
        if (!isAdded() || testFeedback == null) return;

        testFeedback.setText(message);
        testFeedback.setVisibility(View.VISIBLE); // Ensure feedback is visible
        Log.d(TAG, "Feedback Updated: " + message);
    }

    // --- Fragment Lifecycle Cleanup ---
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.i(TAG, "onDestroyView: Releasing resources.");
        // Stop any active processing and release AudioProcessor
        if (audioProcessor != null) {
            // Optional: Call stopTesting() if baseline could potentially be interrupted
            // audioProcessor.stopTesting(); // Tells thread loops to exit
            audioProcessor.release(); // Releases AudioRecord and attempts to join thread
            audioProcessor = null;
        }
        // Nullify view references
        buttonRecordBaseline = null;
        testFeedback = null;
        baselineQualityLabel = null;
        baselineQualityLevel = null;
        // Remove any pending messages from the handler
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "onDestroyView finished.");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        // ActivityResultLauncher is automatically unregistered
        super.onDestroy();
    }
}