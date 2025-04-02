package com.example.vocalharmony.ui;

// Imports (ensure all needed imports are present)
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
import android.widget.Button;
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
import com.example.vocalharmony.ui.home.AudioProcessor; // Make sure path is correct
import com.example.vocalharmony.ui.home.SNRBar; // Make sure path is correct

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Fragment responsible for BOTH recording baseline noise AND
 * conducting the Signal-to-Noise Ratio (SNR) test.
 */
// *** Implement BOTH callback interfaces ***
public class VoiceQualityFragment extends Fragment implements
        AudioProcessor.VoiceQualityTestingCallback,
        AudioProcessor.MicrophoneTestTestingCallback {

    private static final String TAG = "VoiceQualityFragment";
    private static final String PREFS_NAME = "VocalHarmonyPrefs";
    private static final String SNR_KEY_PREFIX = "snr_";

    // --- UI Elements ---
    private SNRBar snrBar;
    private ImageView micStatusIndicator;
    private TextView textCurrentSNRValue;
    private TextView textMaxSNRValue;
    private Button buttonStartSnr;
    private Button buttonStopSnr;
    private Button buttonReset;
    // *** ADDED UI elements for baseline ***
    private Button buttonRecordBaseline;
    private TextView baselineFeedbackVq;
    private TextView baselineQualityLabelVq;
    private TextView baselineQualityLevelVq;


    // --- State & Logic Variables ---
    private AudioProcessor audioProcessor;
    private boolean isTesting = false; // Tracks if SNR test is currently running
    // *** ADDED flag for baseline recording state ***
    private boolean isRecordingBaseline = false;
    private double maxSnrValueSession = Double.NEGATIVE_INFINITY;
    private double loadedBaselinePower = 0.0; // Still useful to know if a baseline exists
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
                        enableInteraction(); // Update button states
                    } else {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_LONG).show();
                        }
                        Log.w(TAG, "❌ Permission denied via launcher.");
                        disableInteraction();
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View rootView = inflater.inflate(R.layout.fragment_voice_quality, container, false);
        initializeUIComponents(rootView); // Find ALL views, including new ones
        setupButtonListeners(); // Setup ALL listeners
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        resetUIState(); // Reset visual state first
        checkMicrophonePermission(); // Check permission and potentially init processor
    }

    // --- UI Initialization ---
    private void initializeUIComponents(View rootView) {
        snrBar = rootView.findViewById(R.id.snr_bar);
        micStatusIndicator = rootView.findViewById(R.id.mic_status_indicator);
        textCurrentSNRValue = rootView.findViewById(R.id.text_current_snr_value);
        textMaxSNRValue = rootView.findViewById(R.id.text_max_snr_value);
        buttonStartSnr = rootView.findViewById(R.id.button_start_snr);
        buttonStopSnr = rootView.findViewById(R.id.button_stop_snr);
        buttonReset = rootView.findViewById(R.id.button_reset);
        // *** Find ADDED baseline views ***
        buttonRecordBaseline = rootView.findViewById(R.id.button_record_baseline);
        baselineFeedbackVq = rootView.findViewById(R.id.baseline_feedback_vq);
        baselineQualityLabelVq = rootView.findViewById(R.id.baseline_quality_label_vq);
        baselineQualityLevelVq = rootView.findViewById(R.id.baseline_quality_level_vq);

        Log.d(TAG, "VoiceQualityFragment UI components initialized.");
    }

    // *** ADDED listener setup ***
    private void setupButtonListeners() {
        if (buttonStartSnr != null) buttonStartSnr.setOnClickListener(v -> startSNRTest());
        if (buttonStopSnr != null) buttonStopSnr.setOnClickListener(v -> stopSNRTest());
        if (buttonReset != null) buttonReset.setOnClickListener(v -> resetTest());
        // *** Add listener for baseline button ***
        if (buttonRecordBaseline != null) buttonRecordBaseline.setOnClickListener(v -> recordBaseline());
    }


    /** Resets the UI elements and state variables */
    private void resetUIState() {
        Log.d(TAG, "Resetting UI state.");
        isTesting = false; // Reset state flags
        isRecordingBaseline = false;

        // Reset SNR Bar and text
        if (snrBar != null) snrBar.reset();
        String defaultValueSNR = getString(R.string.snr_default_value);
        if (textCurrentSNRValue != null) textCurrentSNRValue.setText(defaultValueSNR);
        if (textMaxSNRValue != null) textMaxSNRValue.setText(defaultValueSNR);
        maxSnrValueSession = Double.NEGATIVE_INFINITY;

        // Reset Baseline displays
        if (baselineFeedbackVq != null) baselineFeedbackVq.setText(R.string.baseline_initial_instructions);
        if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE);
        if (baselineQualityLevelVq != null) {
            baselineQualityLevelVq.setText("");
            baselineQualityLevelVq.setVisibility(View.INVISIBLE);
        }

        // Set initial button visibility
        if (buttonStopSnr != null) buttonStopSnr.setVisibility(View.GONE);
        if (buttonStartSnr != null) buttonStartSnr.setVisibility(View.VISIBLE);

        // Update mic indicator and button enable state based on current permission/loaded baseline
        boolean hasPerm = hasAudioPermission();
        updateMicIndicator(hasPerm);
        loadBaselineValue(); // Load baseline value to update 'loadedBaselinePower'
        // enableInteraction(); // enableInteraction is called by loadBaselineValue()
    }

    // --- Permission Handling --- (Largely unchanged)
    private void checkMicrophonePermission() { /* ... same as before ... */
        if (!hasAudioPermission()) {
            Log.d(TAG, "Microphone permission check: NOT granted.");
            updateMicIndicator(false);
            disableInteraction();
        } else {
            Log.d(TAG, "Microphone permission check: Already granted.");
            updateMicIndicator(true);
            initializeAudioProcessorAndLoadBaseline();
            enableInteraction();
        }
    }
    private boolean hasAudioPermission() { /* ... same as before ... */
        if (getContext() == null) return false;
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestAudioPermission() { /* ... same as before ... */
        Log.d(TAG,"Requesting microphone permission via launcher.");
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }
    private void updateMicIndicator(boolean hasPermission) { /* ... same as before ... */
        if (micStatusIndicator != null) {
            micStatusIndicator.setImageResource(hasPermission ? R.drawable.ic_mic_on : R.drawable.ic_mic_off);
        }
    }

    /** Disables all interactive buttons */
    private void disableInteraction() {
        Log.d(TAG,"Disabling interaction.");
        mainHandler.post(() -> {
            if(buttonStartSnr != null) buttonStartSnr.setEnabled(false);
            if(buttonStopSnr != null) buttonStopSnr.setEnabled(false);
            if(buttonReset != null) buttonReset.setEnabled(false);
            if(buttonRecordBaseline != null) buttonRecordBaseline.setEnabled(false); // Disable baseline btn too
        });
    }

    /** Enables/disables buttons based on current state */
    private void enableInteraction() {
        mainHandler.post(() -> {
            if (!isAdded()) { Log.w(TAG, "enableInteraction skipped: Fragment not added."); return; }

            boolean hasPerm = hasAudioPermission();
            boolean baselineReady = loadedBaselinePower > 0.0;

            Log.d(TAG, "Updating interaction state: hasPerm=" + hasPerm + ", baselineReady=" + baselineReady + ", isTesting=" + isTesting + ", isRecordingBaseline=" + isRecordingBaseline);

            // SNR Test Button: Enable if perm granted, baseline exists, AND NOT testing OR recording baseline
            if(buttonStartSnr != null) buttonStartSnr.setEnabled(hasPerm && baselineReady && !isTesting && !isRecordingBaseline);
            // Stop SNR Button: Enable if perm granted AND testing SNR
            if(buttonStopSnr != null) buttonStopSnr.setEnabled(hasPerm && isTesting);
            // Reset Button: Enable if perm granted AND NOT testing SNR OR recording baseline
            if(buttonReset != null) buttonReset.setEnabled(hasPerm && !isTesting && !isRecordingBaseline);
            // Record Baseline Button: Enable if perm granted AND NOT testing SNR OR recording baseline
            if(buttonRecordBaseline != null) buttonRecordBaseline.setEnabled(hasPerm && !isTesting && !isRecordingBaseline);

            // Update baseline button text
            if(buttonRecordBaseline != null){
                if (isRecordingBaseline) {
                    buttonRecordBaseline.setText(R.string.recording_baseline_button_text);
                } else {
                    buttonRecordBaseline.setText(baselineReady ? R.string.baseline_recorded : R.string.record_baseline);
                }
            }
        });
    }

    // --- AudioProcessor Initialization & Baseline Loading ---
    private void initializeAudioProcessorAndLoadBaseline() {
        if (getContext() == null) { Log.e(TAG, "Cannot initialize AudioProcessor: Context is null."); return; }
        if (!hasAudioPermission()){ Log.w(TAG, "Cannot initialize AudioProcessor: Permission not granted."); disableInteraction(); return; }
        if (audioProcessor != null) { Log.d(TAG, "AudioProcessor object already exists."); loadBaselineValue(); return; }

        Log.i(TAG, "Creating AudioProcessor instance...");
        try {
            // *** IMPORTANT: Pass 'this' as BOTH callbacks ***
            audioProcessor = new AudioProcessor(requireContext(), this, this);

            Log.i(TAG, "AudioProcessor instance created successfully.");
            loadBaselineValue(); // Load baseline immediately after successful creation
            enableInteraction(); // Update UI

        } catch (Exception e) {
            Log.e(TAG, "Error creating AudioProcessor instance: " + e.getMessage(), e);
            if(isAdded() && getContext() != null) Toast.makeText(requireContext(), R.string.audio_processor_error, Toast.LENGTH_SHORT).show();
            audioProcessor = null;
            disableInteraction();
        }
    }

    // Load baseline value (same as before)
    private void loadBaselineValue() {
        if (audioProcessor != null) {
            loadedBaselinePower = audioProcessor.getBaselineNoisePower();
            Log.i(TAG, "Retrieved baseline power value via getter: " + loadedBaselinePower);
        } else {
            loadedBaselinePower = 0.0;
            Log.e(TAG, "Cannot retrieve baseline value: AudioProcessor instance is null.");
        }
        enableInteraction(); // Update UI based on loaded value
    }

    // --- Action Methods ---

    /** Starts the SNR test process */
    private void startSNRTest() {
        Log.d(TAG, "Attempting to start SNR Test...");
        // Prerequisite Checks
        if (!hasAudioPermission()) { requestAudioPermission(); return; } // Ask for perm if missing
        if (audioProcessor == null) { initializeAudioProcessorAndLoadBaseline(); return; } // Init if needed
        if (loadedBaselinePower <= 0.0) { // Check if baseline exists
            if(isAdded() && getContext()!= null) Toast.makeText(requireContext(), R.string.record_baseline_first, Toast.LENGTH_LONG).show();
            return;
        }
        if (isTesting || isRecordingBaseline) { Log.w(TAG, "Cannot start SNR test: Already testing or recording baseline."); return; } // Prevent overlap

        Log.i(TAG, "Starting SNR Test execution...");
        maxSnrValueSession = Double.NEGATIVE_INFINITY; // Reset session max
        if(snrBar != null) snrBar.reset();
        String defaultValue = getString(R.string.snr_default_value);
        if (textCurrentSNRValue != null) textCurrentSNRValue.setText(defaultValue);
        if (textMaxSNRValue != null) textMaxSNRValue.setText(defaultValue);

        isTesting = true; // Set state flag
        isRecordingBaseline = false;
        audioProcessor.testMicrophone(); // Start processing

        // Update UI for testing state
        mainHandler.post(() -> {
            if(buttonStartSnr != null) buttonStartSnr.setVisibility(View.GONE);
            if(buttonStopSnr != null) buttonStopSnr.setVisibility(View.VISIBLE);
            enableInteraction(); // Update enabled states
            // Clear baseline feedback/quality when starting SNR test
            if (baselineFeedbackVq != null) baselineFeedbackVq.setText("");
            if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE);
            if (baselineQualityLevelVq != null) baselineQualityLevelVq.setVisibility(View.INVISIBLE);
        });
    }

    /** Stops the SNR test and saves the result */
    private void stopSNRTest() {
        if (!isTesting) return;
        Log.i(TAG, "Stopping SNR Test...");
        isTesting = false; // Set flag first
        if (audioProcessor != null) {
            audioProcessor.stopTesting();
        } else { Log.e(TAG,"Cannot stop test: AudioProcessor is null."); }

        saveMaxSNRResult(); // Save max value

        // Update UI for idle state
        mainHandler.post(() -> {
            if(buttonStopSnr != null) buttonStopSnr.setVisibility(View.GONE);
            if(buttonStartSnr != null) buttonStartSnr.setVisibility(View.VISIBLE);
            enableInteraction(); // Re-enable buttons
            if(baselineFeedbackVq != null) baselineFeedbackVq.setText(getString(R.string.baseline_initial_instructions));
        });
    }

    /** Starts the Baseline recording process */
    private void recordBaseline() {
        Log.d(TAG, "Record Baseline button pressed.");
        // Prerequisite Checks
        if (!hasAudioPermission()) { requestAudioPermission(); return; }
        if (audioProcessor == null) { initializeAudioProcessorAndLoadBaseline(); return; }
        if (isTesting || isRecordingBaseline) { Log.w(TAG, "Cannot record baseline: Already testing or recording baseline."); return; }

        Log.i(TAG, "🎤 Starting baseline recording process...");
        isTesting = false;
        isRecordingBaseline = true; // Set state flag
        if (baselineQualityLabelVq != null) baselineQualityLabelVq.setVisibility(View.INVISIBLE); // Hide old quality
        if (baselineQualityLevelVq != null) baselineQualityLevelVq.setVisibility(View.INVISIBLE);
        if (baselineFeedbackVq != null) baselineFeedbackVq.setText(R.string.baseline_recording_message);
        enableInteraction(); // Update button states (disable others, set baseline btn text)

        audioProcessor.startBaselineRecording(); // Tell processor to start
    }


    /** Resets the displayed values and stops any ongoing test WITHOUT saving */
    private void resetTest() {
        Log.i(TAG, "Resetting test state and UI.");
        boolean wasTesting = isTesting;
        boolean wasRecordingBaseline = isRecordingBaseline;

        // Stop any ongoing process
        isTesting = false;
        isRecordingBaseline = false;
        if (audioProcessor != null) {
            audioProcessor.stopTesting(); // Signal processor to stop whatever it's doing
        }

        if(wasTesting) Log.d(TAG, "Reset called during SNR test. Result NOT saved.");
        if(wasRecordingBaseline) Log.d(TAG, "Reset called during baseline recording.");

        resetUIState(); // Resets display values, max tracker, button visibility/enablement
        if(isAdded() && getContext() != null) Toast.makeText(requireContext(), R.string.test_values_reset, Toast.LENGTH_SHORT).show();
    }

    // --- Data Saving --- (Unchanged)
    private void saveMaxSNRResult() { /* ... same as before ... */
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

    // --- Callback Implementations ---

    // AudioProcessor.VoiceQualityTestingCallback Implementation
    @Override
    public void onIntermediateSNR(final double snr) {
        mainHandler.post(() -> {
            if (!isAdded() || getContext() == null || !isTesting) return; // Only update if testing SNR

            if (Double.isFinite(snr)) {
                if (maxSnrValueSession == Double.NEGATIVE_INFINITY) { maxSnrValueSession = snr; }
                else { maxSnrValueSession = Math.max(maxSnrValueSession, snr); }
            }
            if (snrBar != null) {
                double maxToSend = (maxSnrValueSession > Double.NEGATIVE_INFINITY && Double.isFinite(maxSnrValueSession)) ? maxSnrValueSession : 0.0;
                double currentToSend = Double.isFinite(snr) ? snr : 0.0;
                snrBar.setSNRValue(currentToSend, maxToSend); // *** Ensure SNRBar handles this ***
            }
            String defaultValue = getString(R.string.snr_default_value);
            if (textCurrentSNRValue != null) textCurrentSNRValue.setText(Double.isFinite(snr) ? String.format(Locale.getDefault(), "%.1f dB", snr) : defaultValue);
            if (textMaxSNRValue != null) {
                String maxText = (maxSnrValueSession > Double.NEGATIVE_INFINITY && Double.isFinite(maxSnrValueSession)) ? String.format(Locale.getDefault(), "%.1f dB", maxSnrValueSession) : defaultValue;
                textMaxSNRValue.setText(maxText);
            }
        });
    }

    // AudioProcessor.MicrophoneTestTestingCallback Implementation *** ADDED ***
    @Override
    public void onBaselineRecorded() {
        mainHandler.post(() -> {
            if (!isAdded() || getContext() == null) return;
            Log.i(TAG, "✅ Callback: Baseline recording process completed.");
            isRecordingBaseline = false; // Update state flag

            // Important: Reload the baseline value from processor/prefs
            // This updates loadedBaselinePower which enableInteraction uses
            loadBaselineValue();

            if(baselineFeedbackVq != null) baselineFeedbackVq.setText(getString(R.string.baseline_success_message));
            Toast.makeText(requireContext(), R.string.baseline_recorded_toast, Toast.LENGTH_SHORT).show();
            // enableInteraction() is called by loadBaselineValue()
        });
    }

    @Override
    public void onBaselineQuality(@NonNull String qualityLabel, int qualityLevel) {
        mainHandler.post(() -> {
            if (!isAdded() || getContext() == null) return;
            if (baselineQualityLabelVq == null || baselineQualityLevelVq == null) return;

            String qualityText = getString(R.string.baseline_quality_display_format, qualityLabel, qualityLevel);
            baselineQualityLabelVq.setText(R.string.baseline_quality_label);
            baselineQualityLevelVq.setText(qualityText);
            baselineQualityLabelVq.setVisibility(View.VISIBLE); // Make visible
            baselineQualityLevelVq.setVisibility(View.VISIBLE); // Make visible

            Log.i(TAG, "Callback: Baseline Quality updated - " + qualityText);
        });
    }


    // Shared Callback Implementation (handles calls from BOTH baseline/SNR)
    @Override
    public void onMicrophoneActive(final boolean isActive) {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            Log.d(TAG, isActive ? "🎤 Callback: Mic became active" : "🎤 Callback: Mic became inactive");

            // If mic becomes inactive while we thought we were recording baseline
            if (!isActive && isRecordingBaseline) {
                Log.w(TAG, "Mic became inactive while baseline recording was thought to be active.");
                isRecordingBaseline = false; // Reset flag
                if(baselineFeedbackVq != null) baselineFeedbackVq.setText(getString(R.string.baseline_stopped_message)); // Or error
            }
            // If mic becomes inactive while we thought we were testing SNR
            if (!isActive && isTesting) {
                Log.w(TAG, "Mic became inactive while SNR testing was thought to be active. Stopping test.");
                // We might want to stop the test formally if this happens unexpectedly
                // stopSNRTest(); // Caution: This saves potentially incomplete results. Decide if needed.
                isTesting = false; // Reset flag
            }
            // Always update button states based on current flags
            enableInteraction();
        });
    }

    // --- Fragment Lifecycle Cleanup --- (Unchanged)
    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        // Stop whichever process might be running
        if (isTesting) { Log.w(TAG, "Fragment paused during SNR test. Stopping test and saving result."); stopSNRTest(); }
        if (isRecordingBaseline) { Log.w(TAG, "Fragment paused during baseline recording. Stopping."); isRecordingBaseline = false; if(audioProcessor != null) audioProcessor.stopTesting(); resetUIState();} // Reset UI if baseline interrupted
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView: Cleaning up resources.");
        if (audioProcessor != null) { audioProcessor.release(); audioProcessor = null; }
        snrBar = null; micStatusIndicator = null; textCurrentSNRValue = null; textMaxSNRValue = null;
        buttonStartSnr = null; buttonStopSnr = null; buttonReset = null;
        buttonRecordBaseline = null; baselineFeedbackVq = null; baselineQualityLabelVq = null; baselineQualityLevelVq = null; // Nullify new views
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() { Log.d(TAG, "onDestroy"); super.onDestroy(); }

} // End of VoiceQualityFragment class