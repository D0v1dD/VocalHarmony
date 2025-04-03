// Package statement should be the very first line
package com.example.vocalharmony.ui; // Or your correct package name

// Imports (ensure all are needed and none like android.R are present)
import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.example.vocalharmony.ui.home.AudioProcessor; // Check path
import com.example.vocalharmony.ui.home.SNRBar; // Check path
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Fragment responsible for BOTH baseline recording AND SNR testing.
 */
public class VoiceQualityFragment extends Fragment implements
        AudioProcessor.VoiceQualityTestingCallback,
        AudioProcessor.MicrophoneTestTestingCallback {

    private static final String TAG = "VoiceQualityFragment";
    private static final String PREFS_NAME = "VocalHarmonyPrefs";
    private static final String SNR_KEY_PREFIX = "snr_";

    // --- UI Elements ---
    private SNRBar snrBar;
    private ImageView micStatusIndicator;
    private TextView textFeedback; // General feedback/instructions
    private MaterialButton buttonRecordBaseline;
    private TextView baselineQualityLabelVq; // TextView for "Baseline Quality:" label
    private TextView baselineQualityLevelVq; // TextView for the actual quality value (e.g., "Good (2)")
    private TextView textCurrentSNRValue;
    private TextView textMaxSNRValue;
    private MaterialButton buttonStartSnr;
    private MaterialButton buttonStopSnr;
    private MaterialButton buttonReset;

    // --- State & Logic Variables ---
    private AudioProcessor audioProcessor;
    private boolean isTestingSnr = false; // Is SNR test running?
    private boolean isRecordingBaseline = false; // Is baseline recording running?
    private double maxSnrValueSession = Double.NEGATIVE_INFINITY; // Max SNR achieved in current test run
    private double loadedBaselinePower = 0.0; // Power level of the last recorded/loaded baseline
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // For posting UI updates
    private ActivityResultLauncher<String> requestPermissionLauncher; // For requesting permission

    // --- Fragment Lifecycle & Setup ---

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        // Setup the permission request launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> { // This lambda runs when the user responds to the permission dialog
                    Log.d(TAG, "Permission result received: " + isGranted);
                    updateMicIndicator(isGranted); // Update the mic icon immediately
                    if (isGranted) {
                        Log.d(TAG, "✅ Permission granted via launcher.");
                        // Permission granted, safe to initialize the audio processor
                        initializeAudioProcessorAndLoadBaseline();
                        // updateUiStates() will be called internally by loadBaselineValue()
                    } else {
                        // Permission denied
                        if (isAdded() && getContext() != null) { // Check fragment state before showing toast
                            Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_LONG).show();
                        }
                        Log.w(TAG, "❌ Permission denied via launcher.");
                        updateUiStates(); // Ensure UI reflects lack of permission (buttons disabled)
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // Inflate the layout
        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);
        // Find all UI views by their IDs
        initializeUIComponents(rootView);
        // Set click listeners for the buttons
        setupButtonListeners();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        // Reset UI elements to their initial visual state when view is created
        resetUiVisuals();
        // Check if permission is already granted; if so, initialize AudioProcessor
        checkMicrophonePermission();
    }

    // --- UI Initialization ---
    private void initializeUIComponents(View rootView) {
        snrBar = rootView.findViewById(R.id.snr_bar);
        micStatusIndicator = rootView.findViewById(R.id.mic_status_indicator);
        // *** Ensure these IDs match your fragment_voice_quality.xml layout ***
        textFeedback = rootView.findViewById(R.id.baseline_feedback_vq);
        buttonRecordBaseline = rootView.findViewById(R.id.button_record_baseline);
        baselineQualityLabelVq = rootView.findViewById(R.id.baseline_quality_label_vq);
        baselineQualityLevelVq = rootView.findViewById(R.id.baseline_quality_level_vq);
        // *** --- ***
        textCurrentSNRValue = rootView.findViewById(R.id.text_current_snr_value);
        textMaxSNRValue = rootView.findViewById(R.id.text_max_snr_value);
        buttonStartSnr = rootView.findViewById(R.id.button_start_snr);
        buttonStopSnr = rootView.findViewById(R.id.button_stop_snr);
        buttonReset = rootView.findViewById(R.id.button_reset);

        Log.d(TAG, "UI components initialized.");
        // Optional: Add null checks for critical components if needed
        if (textFeedback == null || buttonRecordBaseline == null || baselineQualityLevelVq == null) {
            Log.e(TAG, "ERROR: One or more Baseline UI components not found! Check layout IDs.");
        }
    }

    private void setupButtonListeners() {
        // Assign click actions to each button
        if (buttonRecordBaseline != null) buttonRecordBaseline.setOnClickListener(v -> recordBaseline());
        if (buttonStartSnr != null) buttonStartSnr.setOnClickListener(v -> startSNRTest());
        if (buttonStopSnr != null) buttonStopSnr.setOnClickListener(v -> stopSNRTest());
        if (buttonReset != null) buttonReset.setOnClickListener(v -> resetActionTriggered()); // Use specific reset handler
        Log.d(TAG, "Button listeners set up.");
    }

    /** Resets the UI display elements to their default visual state */
    private void resetUiVisuals() {
        Log.d(TAG, "Resetting UI visuals.");
        // Reset SNR display elements
        if (snrBar != null) snrBar.reset();
        String defaultValueSNR = getString(R.string.snr_default_value);
        if (textCurrentSNRValue != null) textCurrentSNRValue.setText(defaultValueSNR);
        if (textMaxSNRValue != null) textMaxSNRValue.setText(defaultValueSNR);
        maxSnrValueSession = Double.NEGATIVE_INFINITY; // Reset session max tracker

        // Reset Baseline display elements
        updateFeedback(getString(R.string.baseline_initial_instructions)); // Set initial text
        if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE);
        if (baselineQualityLevelVq != null) {
            baselineQualityLevelVq.setText(""); // Clear text
            baselineQualityLevelVq.setVisibility(View.INVISIBLE); // Hide
        }

        // Update mic icon status
        updateMicIndicator(hasAudioPermission());
        // Load baseline power value from storage (this also calls updateUiStates)
        loadBaselineValue();
    }

    // --- Permission Handling ---
    private void checkMicrophonePermission() {
        boolean hasPermission = hasAudioPermission();
        Log.d(TAG, "Permission check: Granted=" + hasPermission);
        updateMicIndicator(hasPermission); // Update icon
        if (hasPermission) {
            initializeAudioProcessorAndLoadBaseline(); // Initialize if permitted
        } else {
            updateFeedback(getString(R.string.mic_permission_needed_message)); // Show guidance if no permission
        }
        updateUiStates(); // Update buttons based on permission status
    }

    private boolean hasAudioPermission() {
        // Check context before calling requireContext()
        if (getContext() == null) { Log.e(TAG,"hasAudioPermission: Context is null"); return false; }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        Log.d(TAG,"Requesting microphone permission via launcher.");
        // Consider showing rationale here if needed before launching
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void updateMicIndicator(boolean hasPermission) {
        if (micStatusIndicator != null) {
            micStatusIndicator.setImageResource(hasPermission ? R.drawable.ic_mic_on : R.drawable.ic_mic_off);
        }
    }

    /** Central method to update the enabled/visibility state of all buttons. */
    private void updateUiStates() {
        mainHandler.post(() -> { // Ensure runs on UI thread
            if (!isAdded()) { Log.w(TAG, "updateUiStates skipped: Fragment not added."); return; }

            boolean hasPerm = hasAudioPermission();
            boolean baselineReady = loadedBaselinePower > 0.0;
            boolean canStartAction = hasPerm && !isRecordingBaseline && !isTestingSnr;

            // Record Baseline Button State Update (logic unchanged)
            if(buttonRecordBaseline != null) {
                buttonRecordBaseline.setEnabled(canStartAction);
                if (isRecordingBaseline) { buttonRecordBaseline.setText(R.string.recording_baseline_button_text); }
                else { buttonRecordBaseline.setText(baselineReady ? R.string.baseline_recorded : R.string.record_baseline); }
            }

            // Start SNR Button State Update
            if(buttonStartSnr != null) {
                buttonStartSnr.setEnabled(canStartAction && baselineReady);
                int startVisibility = isTestingSnr ? View.GONE : View.VISIBLE;
                // *** ADDED LOGGING ***
                Log.d(TAG, "updateUiStates: Setting buttonStartSnr visibility to " + (startVisibility == View.VISIBLE ? "VISIBLE" : "GONE") + " (isTestingSnr=" + isTestingSnr + ")");
                buttonStartSnr.setVisibility(startVisibility);
            } else { Log.w(TAG, "updateUiStates: buttonStartSnr is null!"); }

            // Stop SNR Button State Update
            if(buttonStopSnr != null) {
                buttonStopSnr.setEnabled(hasPerm && isTestingSnr);
                int stopVisibility = isTestingSnr ? View.VISIBLE : View.GONE;
                // *** ADDED LOGGING ***
                Log.d(TAG, "updateUiStates: Setting buttonStopSnr visibility to " + (stopVisibility == View.VISIBLE ? "VISIBLE" : "GONE") + " (isTestingSnr=" + isTestingSnr + ")");
                buttonStopSnr.setVisibility(stopVisibility);
            } else { Log.w(TAG, "updateUiStates: buttonStopSnr is null!"); }

            // Reset Button State Update (logic unchanged)
            if(buttonReset != null) {
                buttonReset.setEnabled(canStartAction);
            }

            // Overall state log (unchanged)
            Log.d(TAG, "UI States updated log: hasPerm=" + hasPerm + ", baselineReady=" + baselineReady + ", isRecBaseline=" + isRecordingBaseline + ", isTestSnr=" + isTestingSnr);
        });
    }

    // --- AudioProcessor Initialization & Baseline Loading ---
    private void initializeAudioProcessorAndLoadBaseline() {
        // Prevent initialization if context is gone or permission is missing
        if (getContext() == null || !hasAudioPermission()) {
            Log.w(TAG, "Cannot initialize AudioProcessor: Context null or permission missing.");
            updateUiStates(); // Reflect inability to initialize in button states
            return;
        }
        // Avoid re-initialization if instance already exists
        if (audioProcessor != null) {
            Log.d(TAG, "AudioProcessor object already exists.");
            loadBaselineValue(); // Ensure baseline value is loaded and UI updated
            return;
        }

        Log.i(TAG, "Creating AudioProcessor instance...");
        try {
            // Create instance, passing 'this' for BOTH callback interfaces
            audioProcessor = new AudioProcessor(requireContext(), this, this);
            Log.i(TAG, "AudioProcessor instance created successfully.");
            // Load baseline value immediately after creation
            loadBaselineValue();

        } catch (Exception e) { // Catch potential exceptions from AudioProcessor constructor
            Log.e(TAG, "Error creating AudioProcessor instance: " + e.getMessage(), e);
            if(isAdded() && getContext() != null) Toast.makeText(requireContext(), R.string.audio_processor_error, Toast.LENGTH_SHORT).show();
            audioProcessor = null; // Ensure instance is null on failure
            updateUiStates(); // Update UI to reflect failure (buttons disabled)
        }
    }

    /** Loads baseline power value from AudioProcessor's getter and updates UI states */
    private void loadBaselineValue() {
        if (audioProcessor != null) {
            // Get the currently stored baseline power from the processor instance
            loadedBaselinePower = audioProcessor.getBaselineNoisePower();
            Log.i(TAG, "Retrieved baseline power value via getter: " + loadedBaselinePower);
        } else {
            loadedBaselinePower = 0.0; // Assume no baseline if processor is unavailable
            Log.e(TAG, "Cannot retrieve baseline value: AudioProcessor instance is null.");
        }
        // Always update button states after attempting to load baseline
        updateUiStates();
    }

    // --- Action Methods ---

    /** Start Baseline Recording process */
    private void recordBaseline() {
        Log.d(TAG, "Record Baseline button pressed.");
        // Check prerequisites
        if (!hasAudioPermission()) { Log.w(TAG,"Permission missing."); requestAudioPermission(); return; }
        if (audioProcessor == null) { Log.w(TAG,"Processor null, trying init."); initializeAudioProcessorAndLoadBaseline(); if(audioProcessor==null){ Log.e(TAG,"Init failed!"); updateFeedback(getString(R.string.audio_processor_error)); updateUiStates(); return; }}
        // Check if another process is already running
        if (isRecordingBaseline || isTestingSnr) { Log.w(TAG, "Cannot start baseline: Already active."); return; }

        Log.i(TAG, "🎤 Starting baseline recording process...");
        // Set state flags
        isRecordingBaseline = true;
        isTestingSnr = false;

        // Update UI before starting
        updateFeedback(getString(R.string.baseline_recording_message)); // Show "Recording..." message
        if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE); // Hide old quality
        if (baselineQualityLevelVq != null) baselineQualityLevelVq.setVisibility(View.INVISIBLE);
        updateUiStates(); // Update button states (e.g., disable other buttons, set baseline btn text)

        // Tell the processor to start recording
        audioProcessor.startBaselineRecording();
    }

    /** Starts the SNR test process */
    private void startSNRTest() {
        Log.d(TAG, "Start SNR Test button pressed.");
        // Check prerequisites
        if (!hasAudioPermission()){ Log.w(TAG,"Permission missing."); requestAudioPermission(); return; }
        if (audioProcessor == null) { Log.w(TAG,"Processor null, trying init."); initializeAudioProcessorAndLoadBaseline(); if(audioProcessor==null){ Log.e(TAG,"Init failed!"); updateFeedback(getString(R.string.audio_system_not_ready)); updateUiStates(); return; }}
        if (loadedBaselinePower <= 0.0) { Log.w(TAG,"Baseline missing."); if(isAdded()) Toast.makeText(requireContext(), R.string.record_baseline_first, Toast.LENGTH_LONG).show(); return; }
        // Check if another process is already running
        if (isTestingSnr || isRecordingBaseline) { Log.w(TAG, "Cannot start SNR test: Already active."); return; }

        Log.i(TAG, "Starting SNR Test execution...");
        // Reset SNR session values before starting
        maxSnrValueSession = Double.NEGATIVE_INFINITY;
        if(snrBar != null) snrBar.reset();
        String defaultValue = getString(R.string.snr_default_value);
        if (textCurrentSNRValue != null) textCurrentSNRValue.setText(defaultValue);
        if (textMaxSNRValue != null) textMaxSNRValue.setText(defaultValue);

        // Set state flags
        isTestingSnr = true;
        isRecordingBaseline = false;

        // Update UI immediately for the "testing" state
        updateUiStates(); // *** This handles showing Stop button / hiding Start button ***

        // Update feedback and hide irrelevant baseline quality display
        if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE);
        if (baselineQualityLevelVq != null) baselineQualityLevelVq.setVisibility(View.INVISIBLE);
        updateFeedback("SNR test running..."); // Provide user feedback

        // Start audio processing for SNR
        audioProcessor.testMicrophone();
    }

    /** Stops the currently running SNR test and saves the result */
    private void stopSNRTest() {
        // Check if SNR test is actually running
        if (!isTestingSnr) { Log.d(TAG,"Stop SNR requested, but not testing."); return; }
        // Check if processor exists
        if (audioProcessor == null) { Log.e(TAG,"Cannot stop test: AudioProcessor is null."); isTestingSnr = false; updateUiStates(); return; }

        Log.i(TAG, "Stopping SNR Test...");
        isTestingSnr = false; // Set flag immediately to stop processing loop in callbacks

        // Try saving the recorded max SNR value for the session
        saveMaxSNRResult();

        // Signal AudioProcessor to stop reading audio and release resources if needed
        audioProcessor.stopTesting();

        // Update UI back to idle state
        updateUiStates();
        updateFeedback(getString(R.string.baseline_initial_instructions)); // Reset feedback message
    }

    /** Handles the Reset button click. Stops any activity and resets UI. */
    // *** CORRECTED/SIMPLIFIED Logic ***
    private void resetActionTriggered() { // Renamed from resetTestAndBaselineDisplay
        Log.i(TAG, "Reset button pressed. Stopping activity and resetting UI visuals.");

        // Capture state *before* changing flags
        boolean wasPreviouslyTestingSnr = isTestingSnr;
        boolean wasPreviouslyRecordingBaseline = isRecordingBaseline;
        boolean wasActive = wasPreviouslyTestingSnr || wasPreviouslyRecordingBaseline;

        // Set state flags to idle
        isTestingSnr = false;
        isRecordingBaseline = false;

        // Tell processor to stop IF it was active
        if (wasActive && audioProcessor != null) {
            Log.d(TAG, "Telling AudioProcessor to stop.");
            audioProcessor.stopTesting(); // Signal thread to stop
        } else if (wasActive) {
            Log.w(TAG,"Reset called while active, but audioProcessor was null!");
        }

        // Log what was stopped (if anything)
        if(wasPreviouslyTestingSnr) Log.d(TAG, "Reset occurred during SNR test. Result NOT saved.");
        if(wasPreviouslyRecordingBaseline) Log.d(TAG, "Reset occurred during baseline recording.");

        // Reset the visual elements of the UI
        resetUiVisuals(); // Resets text/bar/visibility and calls loadBaselineValue->updateUiStates

        // Show confirmation toast
        if(isAdded() && getContext() != null) Toast.makeText(requireContext(), R.string.test_values_reset, Toast.LENGTH_SHORT).show();
    }

    // --- Data Saving ---
    private void saveMaxSNRResult() {
        // Check fragment/context validity first
        if (getContext() == null || !isAdded()) {
            Log.e(TAG,"Cannot save Max SNR: Context null or fragment not added.");
            // This might happen if stopSNRTest is called during onPause/onDestroyView
            return;
        }
        // Check if a valid max value was actually recorded
        if (maxSnrValueSession <= Double.NEGATIVE_INFINITY || !Double.isFinite(maxSnrValueSession)) {
            Log.w(TAG, "Not saving Max SNR: No valid max value recorded (value=" + maxSnrValueSession + ").");
            return;
        }

        float snrToSave = (float) maxSnrValueSession; // Cast for SharedPreferences

        // Create timestamped key
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());
        String key = SNR_KEY_PREFIX + timestamp;

        Log.i(TAG, String.format(Locale.US, "Saving Max SNR: Key='%s', Value=%.1f", key, snrToSave));
        try {
            // Save to SharedPreferences
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putFloat(key, snrToSave).apply(); // Use apply() for async save
            Log.i(TAG,"Max SNR saved successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save Max SNR to SharedPreferences: " + e.getMessage(), e);
        }
    }

    // --- AudioProcessor.VoiceQualityTestingCallback Implementation ---
    @Override
    public void onIntermediateSNR(final double snr) {
        // Post UI updates to the main thread
        mainHandler.post(() -> {
            // Only process if the fragment is attached and SNR test is running
            if (!isAdded() || !isTestingSnr) return;

            // Update Max SNR tracking
            if (Double.isFinite(snr)) {
                maxSnrValueSession = (maxSnrValueSession == Double.NEGATIVE_INFINITY) ? snr : Math.max(maxSnrValueSession, snr);
            }

            // Update SNRBar (Requires updated SNRBar class accepting current and max)
            if (snrBar != null) {
                double maxToSend = (maxSnrValueSession > Double.NEGATIVE_INFINITY && Double.isFinite(maxSnrValueSession)) ? maxSnrValueSession : 0.0;
                double currentToSend = Double.isFinite(snr) ? snr : 0.0;
                snrBar.setSNRValue(currentToSend, maxToSend); // *** Ensure SNRBar handles this ***
            }

            // Update Numerical TextViews
            String defaultValue = getString(R.string.snr_default_value);
            if (textCurrentSNRValue != null) {
                textCurrentSNRValue.setText(Double.isFinite(snr) ? String.format(Locale.getDefault(), "%.1f dB", snr) : defaultValue);
            }
            if (textMaxSNRValue != null) {
                String maxText = (maxSnrValueSession > Double.NEGATIVE_INFINITY && Double.isFinite(maxSnrValueSession)) ? String.format(Locale.getDefault(), "%.1f dB", maxSnrValueSession) : defaultValue;
                textMaxSNRValue.setText(maxText);
            }
        });
    }

    // --- AudioProcessor.MicrophoneTestTestingCallback Implementation ---
    @Override
    public void onBaselineRecorded() {
        mainHandler.post(() -> {
            if (!isAdded()) return; // Check fragment validity
            Log.i(TAG, "✅ Callback: Baseline recording process completed.");
            isRecordingBaseline = false; // Update state flag FIRST

            // Reload the baseline value from processor/prefs now that it has been updated
            loadBaselineValue(); // This is crucial to update loadedBaselinePower for enableInteraction logic
            // updateUiStates() is called inside loadBaselineValue()

            updateFeedback(getString(R.string.baseline_success_message)); // Show success
            if(getContext() != null) Toast.makeText(requireContext(), R.string.baseline_recorded_toast, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBaselineQuality(@NonNull String qualityLabel, int qualityLevel) {
        mainHandler.post(() -> {
            // Check fragment and view validity
            if (!isAdded() || baselineQualityLevelVq == null || baselineQualityLabelVq == null) return;

            // Format and display the quality level
            String qualityText = getString(R.string.baseline_quality_display_format, qualityLabel, qualityLevel);
            baselineQualityLabelVq.setText(R.string.baseline_quality_label); // Set static label
            baselineQualityLevelVq.setText(qualityText); // Set dynamic quality text
            baselineQualityLabelVq.setVisibility(View.VISIBLE); // Make visible
            baselineQualityLevelVq.setVisibility(View.VISIBLE); // Make visible

            Log.i(TAG, "Callback: Baseline Quality updated - " + qualityText);
        });
    }


    // --- Shared Callback ---
    @Override
    public void onMicrophoneActive(final boolean isActive) {
        mainHandler.post(() -> {
            if (!isAdded()) return; // Check fragment validity
            Log.d(TAG, isActive ? "🎤 Callback: Mic became active" : "🎤 Callback: Mic became inactive");

            // If mic stops unexpectedly, update state flags
            // This helps prevent getting stuck in a recording/testing state if AudioProcessor stops early
            if (!isActive) {
                if (isRecordingBaseline) {
                    isRecordingBaseline = false; // Reset flag
                    Log.w(TAG, "Mic became inactive during baseline recording (unexpected stop?).");
                    updateFeedback(getString(R.string.baseline_stopped_message)); // Update feedback
                }
                if (isTestingSnr) {
                    isTestingSnr = false; // Reset flag
                    Log.w(TAG, "Mic became inactive during SNR testing (unexpected stop?).");
                    updateFeedback("SNR Test stopped unexpectedly."); // Update feedback
                    // Consider if saveMaxSNRResult should be called here or not
                }
            }
            // Always update button enabled/visibility states based on current flags
            updateUiStates();
        });
    }

    // --- Helper to update feedback TextView ---
    private void updateFeedback(String message) {
        if (!isAdded() || textFeedback == null) return;
        // Ensure UI update runs on main thread (it's already inside mainHandler.post in callbacks, but good practice elsewhere)
        mainHandler.post(() -> {
            if (textFeedback != null && isAdded()) { // Double check after post and check fragment again
                textFeedback.setText(message);
            }
        });
        Log.d(TAG, "Feedback Updated: " + message);
    }

    // --- Fragment Lifecycle Cleanup ---
    @Override
    public void onPause() {
        super.onPause(); // Call super first
        Log.d(TAG, "onPause");
        // Stop whichever process might be running to release mic when fragment is paused
        if (isTestingSnr) {
            Log.w(TAG, "Fragment paused during SNR test. Stopping test and saving result.");
            stopSNRTest(); // Handles state, saving, stopping processor
        } else if (isRecordingBaseline) {
            Log.w(TAG, "Fragment paused during baseline recording. Stopping.");
            isRecordingBaseline = false; // Set flag
            if (audioProcessor != null) {
                audioProcessor.stopTesting(); // Signal processor thread to stop
            }
            updateUiStates(); // Update UI to reflect stop
            updateFeedback(getString(R.string.baseline_stopped_message));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView(); // Call super first
        Log.i(TAG, "onDestroyView: Cleaning up resources.");
        // Release AudioProcessor to stop threads and release AudioRecord resources
        if (audioProcessor != null) {
            audioProcessor.release();
            audioProcessor = null;
        }
        // Nullify view references to prevent memory leaks
        snrBar = null; micStatusIndicator = null; textFeedback = null;
        buttonRecordBaseline = null; baselineQualityLabelVq = null; baselineQualityLevelVq = null;
        textCurrentSNRValue = null; textMaxSNRValue = null;
        buttonStartSnr = null; buttonStopSnr = null; buttonReset = null;
        // Remove any pending messages for this fragment from the handler
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "onDestroyView finished.");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        // ActivityResultLauncher is unregistered automatically
    }
} // End of VoiceQualityFragment class