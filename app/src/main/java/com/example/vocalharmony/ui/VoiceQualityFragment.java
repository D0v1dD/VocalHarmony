// Package statement should be the very first line
package com.example.vocalharmony.ui; // Or your correct package name

// Imports
import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources; // Keep if needed by getResourceName in saveMaxSNRResult's exception log
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
    private TextView textFeedback;
    private MaterialButton buttonRecordBaseline;
    private TextView baselineQualityLabelVq;
    private TextView baselineQualityLevelVq;
    private TextView textCurrentSNRValue;
    private TextView textMaxSNRValue;
    private MaterialButton buttonStartSnr;
    private MaterialButton buttonStopSnr;
    private MaterialButton buttonReset;

    // --- State & Logic Variables ---
    private AudioProcessor audioProcessor;
    private boolean isTestingSnr = false;
    private boolean isRecordingBaseline = false;
    private double maxSnrValueSession = Double.NEGATIVE_INFINITY;
    private double loadedBaselinePower = 0.0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // --- Fragment Lifecycle & Setup ---

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Log.d(TAG, "Permission result received: " + isGranted);
                    updateMicIndicator(isGranted);
                    if (isGranted) {
                        Log.d(TAG, "✅ Permission granted via launcher.");
                        initializeAudioProcessorAndLoadBaseline();
                    } else {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_LONG).show();
                        }
                        Log.w(TAG, "❌ Permission denied via launcher.");
                        updateUiStates(); // Ensure buttons reflect lack of permission
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);
        initializeUIComponents(rootView);
        setupButtonListeners();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        // Check permission FIRST, which initializes processor if needed
        checkMicrophonePermission();
        // THEN Reset visuals (which calls loadBaselineValue -> updateUiStates)
        resetUiVisuals();
    }

    // --- UI Initialization ---
    private void initializeUIComponents(View rootView) {
        snrBar = rootView.findViewById(R.id.snr_bar);
        micStatusIndicator = rootView.findViewById(R.id.mic_status_indicator);
        // *** Ensure these IDs match your actual fragment_voice_quality.xml layout ***
        textFeedback = rootView.findViewById(R.id.baseline_feedback_vq);
        buttonRecordBaseline = rootView.findViewById(R.id.button_record_baseline);
        baselineQualityLabelVq = rootView.findViewById(R.id.baseline_quality_label_vq);
        baselineQualityLevelVq = rootView.findViewById(R.id.baseline_quality_level_vq);
        buttonStartSnr = rootView.findViewById(R.id.button_start_snr);
        buttonStopSnr = rootView.findViewById(R.id.button_stop_snr);
        buttonReset = rootView.findViewById(R.id.button_reset);
        buttonRecordBaseline = rootView.findViewById(R.id.button_record_baseline);
        // *** --- ***
        textCurrentSNRValue = rootView.findViewById(R.id.text_current_snr_value);
        textMaxSNRValue = rootView.findViewById(R.id.text_max_snr_value);
        buttonStartSnr = rootView.findViewById(R.id.button_start_snr);
        buttonStopSnr = rootView.findViewById(R.id.button_stop_snr);
        buttonReset = rootView.findViewById(R.id.button_reset);
        if(buttonStartSnr != null) Log.d(TAG, "Found buttonStartSnr: ID=" + getResources().getResourceEntryName(buttonStartSnr.getId())); else Log.e(TAG, "buttonStartSnr NOT FOUND!");
        if(buttonStopSnr != null) Log.d(TAG, "Found buttonStopSnr: ID=" + getResources().getResourceEntryName(buttonStopSnr.getId())); else Log.e(TAG, "buttonStopSnr NOT FOUND!");
        Log.d(TAG, "UI components initialized.");
        Log.d(TAG, "UI components initialized.");
        if (textFeedback == null || buttonRecordBaseline == null || baselineQualityLevelVq == null) {
            Log.e(TAG, "ERROR: One or more Baseline UI components not found! Check IDs (e.g., baseline_feedback_vq).");
        }
    }

    private void setupButtonListeners() {
        if (buttonRecordBaseline != null) buttonRecordBaseline.setOnClickListener(v -> recordBaseline());
        if (buttonStartSnr != null) buttonStartSnr.setOnClickListener(v -> startSNRTest());
        if (buttonStopSnr != null) buttonStopSnr.setOnClickListener(v -> stopSNRTest());
        if (buttonReset != null) buttonReset.setOnClickListener(v -> resetActionTriggered()); // Use specific reset handler
        Log.d(TAG, "Button listeners set up.");
    }


    /** Resets the UI display elements to their default visual state */
    // *** RENAMED from resetUiState from user's last code block back to this for clarity ***
    private void resetUiVisuals() {
        Log.d(TAG, "Resetting UI visuals.");
        // Reset SNR display elements
        if (snrBar != null) snrBar.reset();
        // Use requireContext safely IF we are sure fragment is attached (should be in onViewCreated)
        String defaultValueSNR = isAdded() ? getString(R.string.snr_default_value) : "-- dB";
        if (textCurrentSNRValue != null) textCurrentSNRValue.setText(defaultValueSNR);
        if (textMaxSNRValue != null) textMaxSNRValue.setText(defaultValueSNR);
        maxSnrValueSession = Double.NEGATIVE_INFINITY; // Reset session max tracker

        // Reset Baseline display elements
        String initialFeedback = isAdded() ? getString(R.string.baseline_initial_instructions) : "Record baseline first.";
        updateFeedback(initialFeedback); // Set initial text using helper
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
        updateUiStates(); // Update buttons based on permission status AFTER potential init attempt
    }

    private boolean hasAudioPermission() {
        if (getContext() == null) { Log.e(TAG,"hasAudioPermission: Context is null"); return false; }
        // Use requireContext() only if certain context is not null, getContext() check above helps
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        Log.d(TAG,"Requesting microphone permission via launcher.");
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void updateMicIndicator(boolean hasPermission) {
        if (micStatusIndicator != null) {
            micStatusIndicator.setImageResource(hasPermission ? R.drawable.ic_mic_on : R.drawable.ic_mic_off);
        }
    }


    /** Central method to update the enabled/visibility state of all buttons based on current state */
    private void updateUiStates() {
        mainHandler.post(() -> { // Ensure runs on UI thread
            if (!isAdded()) { Log.w(TAG, "updateUiStates skipped: Fragment not added."); return; }

            boolean hasPerm = hasAudioPermission();
            boolean baselineReady = loadedBaselinePower > 0.0;
            // Can perform an action if has permission AND is not currently recording baseline AND not testing SNR
            boolean canStartAction = hasPerm && !isRecordingBaseline && !isTestingSnr;

            // Record Baseline Button
            if(buttonRecordBaseline != null) {
                buttonRecordBaseline.setEnabled(canStartAction); // Enable only when idle and permitted
                // Set text based on state
                if (isRecordingBaseline) {
                    buttonRecordBaseline.setText(R.string.recording_baseline_button_text);
                } else {
                    buttonRecordBaseline.setText(baselineReady ? R.string.baseline_recorded : R.string.record_baseline);
                }
            }

            // Start SNR Button
            if(buttonStartSnr != null) {
                buttonStartSnr.setEnabled(canStartAction && baselineReady); // Enable only if idle, permitted, AND baseline ready
                int startVisibility = isTestingSnr ? View.GONE : View.VISIBLE;
                Log.d(TAG, "updateUiStates: Setting buttonStartSnr visibility to " + (startVisibility == View.VISIBLE ? "VISIBLE" : "GONE") + " (isTestingSnr=" + isTestingSnr + ")");
                buttonStartSnr.setVisibility(startVisibility);
            } else { Log.w(TAG, "updateUiStates: buttonStartSnr is null!"); }

            // Stop SNR Button
            if(buttonStopSnr != null) {
                buttonStopSnr.setEnabled(hasPerm && isTestingSnr); // Enable only if testing SNR
                int stopVisibility = isTestingSnr ? View.VISIBLE : View.GONE;
                Log.d(TAG, "updateUiStates: Setting buttonStopSnr visibility to " + (stopVisibility == View.VISIBLE ? "VISIBLE" : "GONE") + " (isTestingSnr=" + isTestingSnr + ")");
                buttonStopSnr.setVisibility(stopVisibility);
            } else { Log.w(TAG, "updateUiStates: buttonStopSnr is null!"); }

            // Reset Button
            if(buttonReset != null) {
                buttonReset.setEnabled(canStartAction); // Enable only when idle and permitted
            }

            // Log overall state (moved down slightly for clarity after button logs)
            // Log.d(TAG, "UI States updated log: hasPerm=" + hasPerm + ", baselineReady=" + baselineReady + ", isRecBaseline=" + isRecordingBaseline + ", isTestSnr=" + isTestingSnr);
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
            loadedBaselinePower = audioProcessor.getBaselineNoisePower();
            Log.i(TAG, "Retrieved baseline power value via getter: " + loadedBaselinePower);
        } else {
            loadedBaselinePower = 0.0; // Assume no baseline if processor is unavailable
            // Log is useful but error msg might be alarming if processor just hasn't been created yet
            // Log.e(TAG, "Cannot retrieve baseline value: AudioProcessor instance is null.");
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
        if (isRecordingBaseline || isTestingSnr) { Log.w(TAG, "Cannot start baseline: Already active."); return; }

        Log.i(TAG, "🎤 Starting baseline recording process...");
        isRecordingBaseline = true; // Set state flag
        isTestingSnr = false;

        updateFeedback(getString(R.string.baseline_recording_message));
        if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE); // Hide old quality
        if (baselineQualityLevelVq != null) baselineQualityLevelVq.setVisibility(View.INVISIBLE);
        updateUiStates(); // Update button states

        audioProcessor.startBaselineRecording();
    }

    /** Starts the SNR test process */
    private void startSNRTest() {
        Log.d(TAG, "Start SNR Test button pressed.");
        // Check prerequisites
        if (!hasAudioPermission()){ Log.w(TAG,"Permission missing."); requestAudioPermission(); return; }
        if (audioProcessor == null) { Log.w(TAG,"Processor null, trying init."); initializeAudioProcessorAndLoadBaseline(); if(audioProcessor==null){ Log.e(TAG,"Init failed!"); updateFeedback(getString(R.string.audio_system_not_ready)); updateUiStates(); return; }}
        if (loadedBaselinePower <= 0.0) { Log.w(TAG,"Baseline missing."); if(isAdded()) Toast.makeText(requireContext(), R.string.record_baseline_first, Toast.LENGTH_LONG).show(); return; }
        if (isTestingSnr || isRecordingBaseline) { Log.w(TAG, "Already active."); return; }

        Log.i(TAG, "Starting SNR Test execution...");
        maxSnrValueSession = Double.NEGATIVE_INFINITY;
        if(snrBar != null) snrBar.reset();
        String defaultValue = getString(R.string.snr_default_value);
        if (textCurrentSNRValue != null) textCurrentSNRValue.setText(defaultValue);
        if (textMaxSNRValue != null) textMaxSNRValue.setText(defaultValue);

        isTestingSnr = true;
        isRecordingBaseline = false;

        updateUiStates(); // *** This handles showing Stop button / hiding Start button ***

        if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE);
        if (baselineQualityLevelVq != null) baselineQualityLevelVq.setVisibility(View.INVISIBLE);
        updateFeedback("SNR test running..."); // Provide feedback

        audioProcessor.testMicrophone();
    }

    /** Stops the currently running SNR test and saves the result */
    private void stopSNRTest() {
        if (!isTestingSnr) { Log.d(TAG,"Stop SNR requested, but not testing."); return; }
        if (audioProcessor == null) { Log.e(TAG,"Cannot stop test: AudioProcessor is null."); isTestingSnr = false; updateUiStates(); return; }

        Log.i(TAG, "Stopping SNR Test...");
        isTestingSnr = false; // Set flag immediately

        saveMaxSNRResult(); // Try saving result first

        audioProcessor.stopTesting(); // Signal AudioProcessor

        updateUiStates(); // Update UI to idle state
        updateFeedback(getString(R.string.baseline_initial_instructions)); // Reset feedback message
    }

    /** Handles the Reset button click. Stops any activity and resets UI visuals. */
    // *** CORRECTED/SIMPLIFIED Logic ***
    private void resetActionTriggered() {
        Log.i(TAG, "Reset button pressed. Stopping activity and resetting UI visuals.");

        // Capture current state BEFORE changing flags
        boolean wasPreviouslyTestingSnr = isTestingSnr;
        boolean wasPreviouslyRecordingBaseline = isRecordingBaseline;
        boolean wasActive = wasPreviouslyTestingSnr || wasPreviouslyRecordingBaseline;

        // Set state flags to idle FIRST
        isTestingSnr = false;
        isRecordingBaseline = false;

        // Tell processor to stop IF it was active
        if (wasActive && audioProcessor != null) {
            Log.d(TAG, "Telling AudioProcessor to stop.");
            audioProcessor.stopTesting(); // Signal thread to stop
        } else if (wasActive) {
            Log.w(TAG,"Reset called while active, but audioProcessor was null!");
        }

        // Log based on the captured state
        if(wasPreviouslyTestingSnr) Log.d(TAG, "Reset occurred during SNR test. Result NOT saved.");
        if(wasPreviouslyRecordingBaseline) Log.d(TAG, "Reset occurred during baseline recording.");

        // Reset the visual elements of the UI and update buttons for idle state
        resetUiVisuals(); // Resets text/bar/visibility and calls loadBaselineValue->updateUiStates

        // Show confirmation toast
        if(isAdded() && getContext() != null) Toast.makeText(requireContext(), R.string.test_values_reset, Toast.LENGTH_SHORT).show();
    }

    // --- Data Saving ---
    private void saveMaxSNRResult() {
        if (getContext() == null || !isAdded()) { Log.e(TAG,"Cannot save Max SNR: Context null or fragment not added."); return; }
        if (maxSnrValueSession <= Double.NEGATIVE_INFINITY || !Double.isFinite(maxSnrValueSession)) { Log.w(TAG, "Not saving Max SNR: No valid max value recorded (value=" + maxSnrValueSession + ")."); return; }
        float snrToSave = (float) maxSnrValueSession;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());
        String key = SNR_KEY_PREFIX + timestamp;
        Log.i(TAG, String.format(Locale.US, "Saving Max SNR: Key='%s', Value=%.1f", key, snrToSave));
        try {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putFloat(key, snrToSave).apply();
            Log.i(TAG,"Max SNR saved successfully.");
        } catch (Exception e) { Log.e(TAG, "Failed to save Max SNR: " + e.getMessage(), e); }
    }

    // --- AudioProcessor.VoiceQualityTestingCallback Implementation ---
    @Override
    public void onIntermediateSNR(final double snr) {
        mainHandler.post(() -> {
            if (!isAdded() || !isTestingSnr) return; // Check fragment state and testing flag

            if (Double.isFinite(snr)) { maxSnrValueSession = (maxSnrValueSession == Double.NEGATIVE_INFINITY) ? snr : Math.max(maxSnrValueSession, snr); }
            if (snrBar != null) {
                double maxToSend = (maxSnrValueSession > Double.NEGATIVE_INFINITY && Double.isFinite(maxSnrValueSession)) ? maxSnrValueSession : 0.0;
                double currentToSend = Double.isFinite(snr) ? snr : 0.0;
                snrBar.setSNRValue(currentToSend, maxToSend); // *** Ensure SNRBar handles this ***
            }
            String defaultValue = getString(R.string.snr_default_value);
            if (textCurrentSNRValue != null) textCurrentSNRValue.setText(Double.isFinite(snr) ? String.format(Locale.getDefault(), "%.1f dB", snr) : defaultValue);
            if (textMaxSNRValue != null) { String maxText = (maxSnrValueSession > Double.NEGATIVE_INFINITY && Double.isFinite(maxSnrValueSession)) ? String.format(Locale.getDefault(), "%.1f dB", maxSnrValueSession) : defaultValue; textMaxSNRValue.setText(maxText); }
        });
    }

    // --- AudioProcessor.MicrophoneTestTestingCallback Implementation ---
    @Override
    public void onBaselineRecorded() {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            Log.i(TAG, "✅ Callback: Baseline recording process completed.");
            isRecordingBaseline = false; // Update state flag FIRST
            loadBaselineValue(); // Reload baseline value & update button states via updateUiStates
            updateFeedback(getString(R.string.baseline_success_message));
            if(getContext() != null) Toast.makeText(requireContext(), R.string.baseline_recorded_toast, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBaselineQuality(@NonNull String qualityLabel, int qualityLevel) {
        mainHandler.post(() -> {
            if (!isAdded() || baselineQualityLevelVq == null || baselineQualityLabelVq == null) return;
            String qualityText = getString(R.string.baseline_quality_display_format, qualityLabel, qualityLevel);
            baselineQualityLabelVq.setText(R.string.baseline_quality_label);
            baselineQualityLevelVq.setText(qualityText);
            baselineQualityLabelVq.setVisibility(View.VISIBLE);
            baselineQualityLevelVq.setVisibility(View.VISIBLE);
            Log.i(TAG, "Callback: Baseline Quality updated - " + qualityText);
        });
    }


    // --- Shared Callback ---
    @Override
    public void onMicrophoneActive(final boolean isActive) {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            Log.d(TAG, isActive ? "🎤 Callback: Mic became active" : "🎤 Callback: Mic became inactive");

            // If mic stops unexpectedly, update state flags and UI
            if (!isActive) {
                boolean stateChanged = false;
                if (isRecordingBaseline) {
                    isRecordingBaseline = false; stateChanged = true;
                    Log.w(TAG, "Mic inactive during baseline recording.");
                    updateFeedback(getString(R.string.baseline_stopped_message));
                }
                if (isTestingSnr) {
                    isTestingSnr = false; stateChanged = true;
                    Log.w(TAG, "Mic inactive during SNR testing.");
                    updateFeedback("SNR Test stopped unexpectedly.");
                }
                if (stateChanged) {
                    updateUiStates(); // Update buttons if state changed
                }
            }
            // No need to call updateUiStates if isActive is true,
            // as the calling methods (recordBaseline/startSNRTest) already handle it.
        });
    }

    // --- Helper to update feedback TextView ---
    private void updateFeedback(String message) {
        if (!isAdded() || textFeedback == null) return;
        mainHandler.post(() -> { // Ensure runs on UI thread
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
        // Stop whichever process might be running
        if (isTestingSnr) {
            Log.w(TAG, "Fragment paused during SNR test. Stopping test and saving result.");
            stopSNRTest(); // Handles state, saving, stopping processor
        } else if (isRecordingBaseline) { // Use else if
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
        if (audioProcessor != null) {
            audioProcessor.release();
            audioProcessor = null;
        }
        // Nullify view references
        snrBar = null; micStatusIndicator = null; textFeedback = null;
        buttonRecordBaseline = null; baselineQualityLabelVq = null; baselineQualityLevelVq = null;
        textCurrentSNRValue = null; textMaxSNRValue = null;
        buttonStartSnr = null; buttonStopSnr = null; buttonReset = null;
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "onDestroyView finished.");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}