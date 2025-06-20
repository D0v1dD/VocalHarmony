package com.example.vocalharmony.ui.dashboard; // Your package

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.MediaRecorder;
// Removed Uri import - not needed for local only
import android.os.Bundle;
// Removed Handler/Looper imports - not needed
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

// Removed WorkManager, Firebase, UUID imports

import com.example.vocalharmony.R; // Make sure R is imported correctly
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A fragment for recording user audio while they read prompted sentences.
 * Handles displaying sentences, requesting permission, recording audio locally,
 * and managing UI states. (Cloud upload deferred).
 */
public class SentenceRecordingFragment extends Fragment {

    private static final String TAG = "SentenceRecordingFrag";

    // --- UI Elements ---
    private TextView promptSentenceTextView;
    private MaterialButton recordButton;
    private MaterialButton saveButton; // Now acts as "Confirm Local Save"
    private MaterialButton nextSentenceButton;
    private TextView statusTextView;

    // --- State Variables ---
    private volatile boolean isRecording = false;
    private String currentSentence = "";
    private File currentRecordingFile = null; // Path to the latest successfully stopped recording

    // --- Sentence Management ---
    private List<String> sentenceList;
    private List<String> remainingSentences;
    private final Random randomGenerator = new Random();

    // --- Permission Handling ---
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private boolean hasAudioPermission = false;

    // --- MediaRecorder ---
    private MediaRecorder mediaRecorder = null;

    // --- Lifecycle Methods ---

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // Initialize Permission Launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Log.d(TAG, "RECORD_AUDIO Permission result received: " + isGranted);
                    if (isGranted) {
                        hasAudioPermission = true;
                        if (getContext() != null) Toast.makeText(getContext(), "Audio permission granted.", Toast.LENGTH_SHORT).show();
                        updateButtonStates();
                    } else {
                        hasAudioPermission = false;
                        if (getContext() != null) Toast.makeText(getContext(), "Audio permission denied. Cannot record.", Toast.LENGTH_LONG).show();
                        updateButtonStates();
                    }
                });

        // Load sentences from resources
        loadSentences();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        return inflater.inflate(R.layout.fragment_sentence_recording, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        promptSentenceTextView = view.findViewById(R.id.prompt_sentence_textview);
        recordButton = view.findViewById(R.id.record_button);
        saveButton = view.findViewById(R.id.save_button);
        nextSentenceButton = view.findViewById(R.id.next_sentence_button);
        statusTextView = view.findViewById(R.id.status_textview);

        updatePermissionStatus();
        setupInitialState();
        setupButtonClickListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        updatePermissionStatus();
        updateButtonStates();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (isRecording) {
            Log.w(TAG, "Fragment paused during recording. Stopping and releasing recorder.");
            try { if(mediaRecorder != null) { mediaRecorder.stop(); Log.d(TAG,"MediaRecorder stopped in onPause."); } }
            catch (Exception e) { Log.e(TAG,"Error stopping recorder in onPause", e); } // Catch generic Exception as stop can throw Runtime variants
            releaseMediaRecorder();
            isRecording = false;
            currentRecordingFile = null;
            updateButtonStates();
            if(isAdded() && statusTextView != null) statusTextView.setText(R.string.status_prompt_stopped);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
        releaseMediaRecorder();
        promptSentenceTextView = null; recordButton = null; saveButton = null; nextSentenceButton = null; statusTextView = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        releaseMediaRecorder();
    }

    // --- Permission Handling Methods (Keep as before) ---

    private void updatePermissionStatus() {
        if (getContext() != null) { hasAudioPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; Log.d(TAG, "Updated permission status: hasAudioPermission = " + hasAudioPermission); }
        else { Log.w(TAG, "updatePermissionStatus: Context is null!"); hasAudioPermission = false; }
    }

    private boolean checkAndRequestAudioPermission() {
        if (!isAdded() || getContext() == null || requestPermissionLauncher == null) { Log.e(TAG,"checkAndRequestAudioPermission: Prerequisites not met."); return false; }
        updatePermissionStatus();
        if (hasAudioPermission) { Log.d(TAG, "Permission check: Already granted."); return true; }
        else { Log.d(TAG, "Permission check: Not granted. Requesting..."); requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return false; }
    }

    // --- Sentence Handling (Keep as before) ---

    private void loadSentences() {
        try {
            if (getContext() == null) { Log.e(TAG, "Cannot load sentences, context is null."); sentenceList = new ArrayList<>(); remainingSentences = new ArrayList<>(); return; }
            Resources res = requireContext().getResources(); sentenceList = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.rainbow_passage_sentences))); remainingSentences = new ArrayList<>(sentenceList);
            Collections.shuffle(remainingSentences, randomGenerator); Log.d(TAG, "Loaded and shuffled " + sentenceList.size() + " sentences.");
        } catch (Resources.NotFoundException e){
            Log.e(TAG, "Sentence array resource not found!", e); sentenceList = new ArrayList<>(); remainingSentences = new ArrayList<>();
            if(isAdded()) Toast.makeText(getContext(), "Error: Sentence list not found.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error loading sentences from resources", e); sentenceList = new ArrayList<>(); remainingSentences = new ArrayList<>(); if(isAdded()) Toast.makeText(getContext(), "Error loading sentences.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadNextSentence() {
        Log.d(TAG, "loadNextSentence called");
        if (isRecording) { Log.w(TAG,"Loading next sentence while recording - stopping first."); stopRecording(); }
        currentRecordingFile = null; isRecording = false;

        if (remainingSentences == null || sentenceList == null) {
            Log.e(TAG,"Sentence lists not initialized!"); loadSentences();
            if (remainingSentences == null || sentenceList == null || sentenceList.isEmpty()) { if(isAdded() && promptSentenceTextView != null) { promptSentenceTextView.setText("Error: Sentences unavailable."); statusTextView.setText("Error"); } updateButtonStates(); return; }
        }
        if (remainingSentences.isEmpty()) {
            Log.d(TAG,"Sentence round complete, reshuffling.");
            if (sentenceList.isEmpty()) { Log.e(TAG,"Master sentence list empty!"); if(isAdded() && promptSentenceTextView != null) { promptSentenceTextView.setText("No sentences defined."); statusTextView.setText("Setup Error"); } currentSentence = ""; updateButtonStates(); return; }
            remainingSentences.addAll(sentenceList); Collections.shuffle(remainingSentences, randomGenerator);
            if(isAdded()) Toast.makeText(getContext(), "Starting next round of sentences.", Toast.LENGTH_SHORT).show();
        }
        if (!remainingSentences.isEmpty()) { currentSentence = remainingSentences.remove(0); if(isAdded() && promptSentenceTextView != null) { promptSentenceTextView.setText(currentSentence); } Log.d(TAG, "Displayed sentence: \"" + currentSentence + "\""); }
        else { Log.e(TAG,"No sentences available after reshuffle."); if(isAdded() && promptSentenceTextView != null) { promptSentenceTextView.setText("Error loading sentence."); } currentSentence = ""; }

        if(isAdded() && statusTextView != null) statusTextView.setText(getString(R.string.status_prompt_ready));
        updateButtonStates();
    }

    // --- UI Setup and State Management (Keep as before) ---

    private void setupInitialState() { Log.d(TAG, "Setting up initial UI state."); loadNextSentence(); }

    /** Updates the enabled/disabled state and text of buttons based on current state flags. */
    private void updateButtonStates() {
        // Add checks for null views in case called before onViewCreated fully completes or after onDestroyView
        if (!isAdded() || recordButton == null || saveButton == null || nextSentenceButton == null || statusTextView == null) return;

        // --- Record/Stop Button ---
        recordButton.setText(isRecording ? getString(R.string.record_button_stop) : getString(R.string.record_button_start));
        recordButton.setIconResource(isRecording ? android.R.drawable.ic_media_pause : android.R.drawable.ic_btn_speak_now);
        // ** CORRECTED LOGIC **: Enable the button if permission is granted, regardless of recording state.
        // The listener handles whether start or stop is called.
        recordButton.setEnabled(hasAudioPermission);

        // --- Save Button ---
        // Enable Save only if permission granted, NOT recording, AND a valid recording file exists
        saveButton.setEnabled(hasAudioPermission && !isRecording && currentRecordingFile != null);

        // --- Next Sentence Button ---
        // Enable Next only if NOT recording
        nextSentenceButton.setEnabled(!isRecording);

        // Log the state (optional but helpful for debugging)
        Log.d(TAG, "Updated Button States: RecordEnabled=" + recordButton.isEnabled() +
                ", SaveEnabled=" + saveButton.isEnabled() + ", NextEnabled=" + nextSentenceButton.isEnabled() +
                ", HasPerm=" + hasAudioPermission + ", IsRecording=" + isRecording +
                ", RecordingExists=" + (currentRecordingFile != null));

        // Update status text only if idle (not recording, no file ready to save)
        // (Keep this logic as is)
        if(!isRecording && currentRecordingFile == null) {
            statusTextView.setText(hasAudioPermission ? getString(R.string.status_prompt_ready) : getString(R.string.permission_needed_to_record));
        }
    }

    // --- Button Click Handlers (Keep as before) ---

    private void setupButtonClickListeners() {
        Log.d(TAG, "Setting up button click listeners.");
        if (recordButton == null || saveButton == null || nextSentenceButton == null) { Log.e(TAG, "Buttons null in setupButtonClickListeners!"); return; }
        recordButton.setOnClickListener(v -> { if (isRecording) { stopRecording(); } else { startRecording(); } });
        saveButton.setOnClickListener(v -> saveAndUploadRecording()); // This now calls the simplified local save version
        nextSentenceButton.setOnClickListener(v -> loadNextSentence());
    }

    // --- Recording Logic (Keep as before) ---

    private void startRecording() {
        // ... (Keep the full startRecording implementation using MediaRecorder as provided previously) ...
        Log.d(TAG, "startRecording button pressed");
        if (!checkAndRequestAudioPermission()) { Log.w(TAG, "Audio permission check failed or pending."); return; }
        if (isRecording) { Log.w(TAG, "Start called while already recording."); return; }
        if (getContext() == null) { Log.e(TAG, "Start recording failed: Context is null."); return; }

        File recordingsDir = new File(requireContext().getFilesDir(), "sentence_recordings");
        if (!recordingsDir.exists() && !recordingsDir.mkdirs()) { Log.e(TAG, "Failed to create recordings directory: " + recordingsDir.getAbsolutePath()); if(isAdded()) statusTextView.setText("Error: Cannot create storage directory."); return; }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sentenceHash = String.valueOf(currentSentence.hashCode());
        String fileName = "Rec_" + sentenceHash + "_" + timestamp + ".m4a";
        currentRecordingFile = new File(recordingsDir, fileName);
        Log.d(TAG, "Output file target: " + currentRecordingFile.getAbsolutePath());

        releaseMediaRecorder();
        mediaRecorder = new MediaRecorder();
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentRecordingFile.getAbsolutePath());
            mediaRecorder.prepare();
            Log.d(TAG,"MediaRecorder prepared for " + currentRecordingFile.getName());
            mediaRecorder.start();
            Log.i(TAG,"MediaRecorder started.");
            isRecording = true;
            if(isAdded() && statusTextView != null) statusTextView.setText(R.string.status_prompt_recording);
            updateButtonStates();
        } catch (IOException | IllegalStateException | SecurityException e) {
            Log.e(TAG, "MediaRecorder setup/start failed for " + currentRecordingFile.getName(), e);
            if(isAdded() && statusTextView != null) statusTextView.setText(getString(R.string.status_error_prefix) + "Recorder start failed");
            releaseMediaRecorder(); currentRecordingFile = null; updateButtonStates();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error starting recorder", e); if(isAdded() && statusTextView != null) statusTextView.setText(getString(R.string.status_error_prefix) + "Unknown recorder error");
            releaseMediaRecorder(); currentRecordingFile = null; updateButtonStates();
        }
    }

    private void stopRecording() {
        // ... (Keep the full stopRecording implementation using MediaRecorder as provided previously, including the corrected catch block) ...
        Log.d(TAG, "stopRecording requested");
        if (!isRecording) { Log.w(TAG,"Stop called but not recording."); return; }
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop(); Log.i(TAG,"MediaRecorder stopped successfully for " + (currentRecordingFile != null ? currentRecordingFile.getName() : "unknown file"));
                isRecording = false;
            } catch (RuntimeException e) { // Catch RuntimeException and subtypes like IllegalStateException
                Log.e(TAG, "MediaRecorder stop() failed", e); if(isAdded() && statusTextView != null) { statusTextView.setText(getString(R.string.status_error_prefix) + "Stopping recording failed"); }
                if (currentRecordingFile != null && currentRecordingFile.exists()) { Log.w(TAG, "Deleting potentially corrupt file: " + currentRecordingFile.getName()); if(!currentRecordingFile.delete()) { Log.e(TAG, "Failed to delete corrupt recording file."); } }
                currentRecordingFile = null; isRecording = false;
            } finally {
                releaseMediaRecorder();
            }
        } else { Log.w(TAG,"Stop called while isRecording=true, but mediaRecorder was null!"); isRecording = false; currentRecordingFile = null; }
        if(isAdded() && statusTextView != null) { statusTextView.setText(currentRecordingFile != null ? R.string.status_prompt_stopped : R.string.status_error_saving); }
        updateButtonStates();
    }

    private void releaseMediaRecorder() {
        // ... (Keep the full releaseMediaRecorder implementation as provided previously) ...
        if (mediaRecorder != null) {
            Log.d(TAG, "Releasing MediaRecorder resource...");
            try { mediaRecorder.reset(); mediaRecorder.release(); Log.d(TAG, "MediaRecorder released."); }
            catch (Exception e) { Log.e(TAG, "Exception during MediaRecorder reset/release", e); }
            finally { mediaRecorder = null; }
        }
    }

    // --- MODIFIED Save Logic (Local Only) ---

    /**
     * Confirms local save is complete and resets state for the next recording.
     * (Upload functionality deferred).
     */
    private void saveAndUploadRecording() { // Method name kept for now, but only saves locally
        Log.d(TAG, "saveAndUploadRecording called (Local Save Confirmation)");

        // 1. Validate State (Ensure a recording exists)
        if (currentRecordingFile == null || !currentRecordingFile.exists()) {
            Log.e(TAG, "Save clicked but currentRecordingFile is null or doesn't exist!");
            // Check fragment state before showing Toast
            if(isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "No valid recording found to save.", Toast.LENGTH_SHORT).show();
            }
            currentRecordingFile = null; // Ensure reference is cleared if invalid
            updateButtonStates();
            return;
        }

        final File savedFile = currentRecordingFile; // Keep reference for logging/toast

        Log.i(TAG, "Recording confirmed saved locally: " + savedFile.getName() + " at " + savedFile.getAbsolutePath());

        // 2. Update UI Feedback
        if(isAdded() && getContext() != null) {
            // Make sure statusTextView is not null before using
            if (statusTextView != null) {
                statusTextView.setText("Recording saved locally"); // Change status message
            }
            Toast.makeText(getContext(), "Saved: " + savedFile.getName(), Toast.LENGTH_SHORT).show();
        }

        // 3. Reset State for Next Recording
        currentRecordingFile = null; // Clear the reference to the saved file
        isRecording = false;         // Ensure recording state is false
        updateButtonStates();        // Re-enable Record/Next, disable Save
    }

    // --- REMOVED observeUploadWork method ---
    // private void observeUploadWork(...) { ... }

} // End of Fragment Class