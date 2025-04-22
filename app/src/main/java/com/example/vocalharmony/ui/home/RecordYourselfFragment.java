package com.example.vocalharmony.ui.home;

// Android & Java Imports
import android.Manifest;
import android.content.Context;
import android.content.DialogInterface; // Needed for AlertDialog
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder; // Keep for AudioSource constants
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText; // Needed for rename dialog
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.Resources; // Keep for header update exception logging

// AndroidX Imports
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog; // Needed for delete/rename confirmation
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Project Specific Imports
import com.example.vocalharmony.R; // Ensure your R file is correctly imported
import com.google.android.material.button.MaterialButton; // Use if buttons are MaterialButtons

// Java IO and Util Imports
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern; // For filename validation

/**
 * Fragment for recording audio messages, listing them, and allowing playback,
 * sharing, deletion, and renaming.
 */
public class RecordYourselfFragment extends Fragment implements RecordingsAdapter.OnItemActionListener {

    // --- Constants ---
    private static final String TAG = "RecordYourselfFragment";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = Math.max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            2048 // Use a reasonable default buffer size
    );
    private static final String FILENAME_PREFIX = "VocalHarmony_";
    private static final String FILENAME_SUFFIX = ".wav";
    // Pattern to check for invalid filename characters (adjust as needed for target OS)
    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");


    // --- UI Elements ---
    private MaterialButton recordButton;
    private MaterialButton stopButton;
    private TextView statusTextView;
    private RecyclerView recyclerViewRecordings;

    // --- State & Logic Variables ---
    private AudioRecord audioRecord;
    private MediaPlayer mediaPlayer;
    private File currentRecordingFile; // File currently being written to
    private volatile boolean isRecording = false; // Flag for recording state (volatile for thread visibility)
    private boolean isPlaying = false; // Flag for playback state
    private String playingFilePath = null; // Path of the file currently playing

    private RecordingsAdapter recordingsAdapter;
    // Use a final list instance, modify its contents (clear/addAll)
    private final List<RecordingItem> recordingItemsList = new ArrayList<>();

    // Background tasks executor and UI thread handler
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Permission request launcher
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // --- Lifecycle & Permissions ---

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        // Initialize the permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Log.d(TAG, "Permission result received: " + isGranted);
                    if (isGranted) {
                        if(getContext() != null) Toast.makeText(requireContext(), "Permission granted. Ready.", Toast.LENGTH_SHORT).show();
                        // Re-check state and load list now that permission is granted
                        checkPermissionAndSetup();
                    } else {
                        if(getContext() != null) Toast.makeText(requireContext(), R.string.permission_required_to_record, Toast.LENGTH_LONG).show();
                        updateStatusText(getString(R.string.permission_denied_cant_record));
                        // Ensure UI reflects lack of permission
                        updateUiForCurrentState();
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View root = inflater.inflate(R.layout.fragment_record_yourself, container, false);

        // Initialize UI elements
        statusTextView = root.findViewById(R.id.status_text_view);
        recordButton = root.findViewById(R.id.record_button);
        stopButton = root.findViewById(R.id.stop_button);
        recyclerViewRecordings = root.findViewById(R.id.recycler_view_recordings);

        // Setup RecyclerView and button listeners
        setupRecyclerView();
        setupButtonListeners();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        // Initial check for permission and setup UI/load data
        checkPermissionAndSetup();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        // Refresh list and UI state when fragment resumes
        // Ensure permission check happens again in case it changed while paused
        checkPermissionAndSetup();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG,"onPause called.");
        // Crucial: Stop any active recording or playback when the fragment is paused
        if (isRecording) {
            Log.w(TAG, "Fragment paused during recording. Stopping recording.");
            stopRecording(); // Request stop
        }
        if (isPlaying) {
            Log.w(TAG, "Fragment paused during playback. Stopping playback.");
            stopPlaying();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG,"onDestroyView: Nullifying views and removing handler callbacks.");
        // Clean up UI references and pending UI updates
        mainHandler.removeCallbacksAndMessages(null);
        recyclerViewRecordings.setAdapter(null); // Prevent memory leaks from adapter
        recyclerViewRecordings = null;
        recordingsAdapter = null; // Let adapter be garbage collected
        statusTextView = null;
        recordButton = null;
        stopButton = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Releasing audio resources & shutting down executor.");
        // Release native resources and background thread
        releaseAudioRecord();
        releaseMediaPlayer();
        shutdownExecutorService();
    }

    // --- Setup Methods ---

    /** Checks audio permission, requests if necessary, and loads recordings if granted. */
    private void checkPermissionAndSetup() {
        Log.d(TAG, "checkPermissionAndSetup called.");
        if (hasAudioPermission()) {
            Log.d(TAG, "Permission already granted.");
            // Load recordings only if permission is granted
            loadRecordingsList();
        } else {
            Log.d(TAG, "Permission not granted. Requesting...");
            updateStatusText(getString(R.string.permission_needed_to_record));
            // Optionally request permission immediately here, or wait for user action
            // requestAudioPermission();
            // Ensure the list is cleared if permission is denied/missing
            if (recordingsAdapter != null) {
                recordingItemsList.clear();
                recordingsAdapter.submitList(recordingItemsList); // Show empty list
            }
        }
        // Update button states based on current permission status
        updateUiForCurrentState();
    }

    /** Configures the RecyclerView with its adapter and layout manager. */
    private void setupRecyclerView() {
        if (getContext() == null || recyclerViewRecordings == null) {
            Log.e(TAG, "Failed to setup RecyclerView - context or view is null");
            return;
        }
        // Create adapter only if it doesn't exist
        if (recordingsAdapter == null) {
            recordingsAdapter = new RecordingsAdapter(requireContext(), this);
            Log.d(TAG, "New RecordingsAdapter created.");
        }
        // Set layout manager and adapter
        recyclerViewRecordings.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewRecordings.setAdapter(recordingsAdapter);
        // Optional: Add item decoration (like dividers)
        // recyclerViewRecordings.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        Log.d(TAG, "RecyclerView setup complete.");
    }

    /** Sets onClick listeners for the record and stop buttons. */
    private void setupButtonListeners() {
        if (recordButton != null) {
            recordButton.setOnClickListener(v -> {
                if (hasAudioPermission()) {
                    startRecording(); // Start if permission exists
                } else {
                    requestAudioPermission(); // Request if permission missing
                }
            });
        } else { Log.e(TAG, "Record button is null!"); }

        if (stopButton != null) {
            stopButton.setOnClickListener(v -> {
                if (isRecording) {
                    stopRecording(); // Stop only if currently recording
                }
            });
        } else { Log.e(TAG, "Stop button is null!"); }
        Log.d(TAG, "Button listeners set up.");
    }

    // --- Permission Helper Methods ---

    /** Checks if the RECORD_AUDIO permission is granted. */
    private boolean hasAudioPermission() {
        if (getContext() == null) {
            Log.e(TAG,"hasAudioPermission check failed: Context is null");
            return false;
        }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Requests the RECORD_AUDIO permission using the ActivityResultLauncher. */
    private void requestAudioPermission() {
        Log.d(TAG,"Requesting microphone permission via launcher.");
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // Explain why the permission is needed (optional)
            Toast.makeText(requireContext(), R.string.permission_rationale_recording, Toast.LENGTH_LONG).show();
        }
        // Launch the permission request
        if (requestPermissionLauncher != null) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            Log.e(TAG, "requestPermissionLauncher is null, cannot request permission.");
            if(getContext() != null) Toast.makeText(requireContext(), "Error requesting permission.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- File Management ---

    /** Loads the list of existing WAV recordings from the app's external files directory. */
    private void loadRecordingsList() {
        if (getContext() == null || recordingsAdapter == null) {
            Log.e(TAG, "Cannot load recordings: Context or Adapter is null.");
            return;
        }
        Log.d(TAG, "Loading recordings list...");
        // Execute file loading on a background thread
        executorService.execute(() -> {
            List<RecordingItem> loadedItems = new ArrayList<>();
            File recordingsDir = requireContext().getExternalFilesDir(null); // App-specific external storage

            if (recordingsDir != null && recordingsDir.exists()) {
                // Filter for files matching the naming convention
                File[] files = recordingsDir.listFiles((dir, name) ->
                        name.startsWith(FILENAME_PREFIX) && name.toLowerCase().endsWith(FILENAME_SUFFIX));

                if (files != null) {
                    Log.d(TAG, "Found " + files.length + " potential recording files.");
                    // Prepare date/time formatters
                    SimpleDateFormat durationFormat = new SimpleDateFormat("m:ss", Locale.getDefault());
                    durationFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // Format duration correctly
                    SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()); // More detailed date

                    for (File file : files) {
                        try {
                            String name = file.getName();
                            // Extract timestamp from filename
                            String timestampStr = name.substring(FILENAME_PREFIX.length(), name.length() - FILENAME_SUFFIX.length());
                            long timestamp = Long.parseLong(timestampStr);

                            // Create RecordingItem
                            RecordingItem item = new RecordingItem(name, file.getAbsolutePath(), timestamp);

                            // Get duration using MediaPlayer (safely)
                            MediaPlayer mp = null;
                            try {
                                mp = new MediaPlayer();
                                mp.setDataSource(item.getFilePath());
                                mp.prepare(); // Can throw IOException if file is corrupt/invalid
                                long durationMs = mp.getDuration();
                                item.setDurationString(durationMs > 0 ? durationFormat.format(new Date(durationMs)) : "0:00");
                            } catch (Exception e) { // Catch MediaPlayer exceptions (IOException, IllegalStateException)
                                Log.w(TAG, "Could not get duration for " + item.getFileName() + ". File might be corrupt.", e);
                                item.setDurationString("?:??"); // Indicate error
                            } finally {
                                if (mp != null) { try { mp.release(); } catch (Exception ignored) {} }
                            }

                            // Set formatted date string
                            item.setFormattedDate(displayDateFormat.format(new Date(item.getTimestamp())));
                            loadedItems.add(item);

                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Could not parse timestamp from filename: " + file.getName() + ". Skipping.");
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing file entry: " + file.getName(), e);
                        }
                    } // End loop through files

                    // Sort by timestamp, newest first
                    loadedItems.sort(Comparator.comparingLong(RecordingItem::getTimestamp).reversed());

                } else { Log.w(TAG, "listFiles returned null for directory: " + recordingsDir.getPath()); }
            } else { Log.w(TAG, "Recordings directory is null or doesn't exist."); }

            Log.d(TAG, "Finished loading. Found " + loadedItems.size() + " valid recordings.");

            // Update UI on the main thread
            mainHandler.post(() -> {
                if (!isAdded() || recordingsAdapter == null) return; // Check fragment/adapter state
                recordingItemsList.clear();
                recordingItemsList.addAll(loadedItems);
                recordingsAdapter.submitList(recordingItemsList); // Update the adapter's list
                updateUiForCurrentState(); // Refresh button states etc.
            });
        });
    }

    /**
     * Shows a confirmation dialog and deletes the specified recording file and list item.
     * (Corrected version using only notifyItemRemoved)
     */
    private void deleteRecording(final RecordingItem item, final int position) {
        if (item == null || item.getFilePath() == null || !isAdded() || getContext() == null) {
            Log.e(TAG,"deleteRecording: Invalid input or fragment state."); return;
        }

        File fileToDelete = new File(item.getFilePath());
        String fileName = fileToDelete.getName(); // Get name for dialog

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, fileName)) // Use formatted string
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    // Perform deletion on background thread
                    executorService.execute(() -> {
                        boolean deleted = false;
                        String logMessage = "Attempting delete for " + fileName;
                        try {
                            if (fileToDelete.exists()) {
                                deleted = fileToDelete.delete();
                                logMessage = "Deletion attempt result for " + fileName + ": " + deleted;
                            } else {
                                Log.w(TAG,"File not found for deletion: " + fileName + ". Treating as 'deleted'.");
                                deleted = true; // Allow list item removal if file is already gone
                            }
                        } catch (SecurityException se) {
                            logMessage = "SecurityException deleting file " + fileName;
                            Log.e(TAG, logMessage, se);
                        } catch (Exception e) {
                            logMessage = "Error deleting file " + fileName;
                            Log.e(TAG, logMessage, e);
                        } finally {
                            Log.d(TAG, logMessage); // Log the final outcome/error
                        }

                        // --- Actions after delete attempt ---
                        final boolean finalDeleted = deleted; // Need final var for lambda
                        mainHandler.post(() -> { // Ensure UI updates are on the main thread
                            if (!isAdded()) return; // Check fragment state again

                            if (finalDeleted) {
                                Log.i(TAG, "File deletion successful (or file was missing): " + fileName);
                                // Verify position and item consistency *before* removing from list
                                if (position >= 0 && position < recordingItemsList.size() && recordingItemsList.get(position).equals(item)) { // Use .equals for safety
                                    recordingItemsList.remove(position);
                                    if (recordingsAdapter != null) {
                                        Log.d(TAG, "Notifying adapter: item removed at position " + position);
                                        recordingsAdapter.notifyItemRemoved(position);
                                        // No longer calling notifyItemRangeChanged here
                                    }
                                } else {
                                    // If position is invalid or item doesn't match, log it and reload the whole list as a fallback
                                    Log.w(TAG,"Position " + position + " invalid or item mismatch during deletion UI update. Reloading list.");
                                    loadRecordingsList();
                                }
                                updateUiForCurrentState(); // Update overall UI state if needed
                            } else {
                                // Deletion failed
                                Log.e(TAG, "Failed to delete file: " + fileName);
                                Toast.makeText(requireContext(), R.string.error_deleting_file, Toast.LENGTH_SHORT).show();
                            }
                        }); // End mainHandler.post
                    }); // End executorService.execute
                })
                .setNegativeButton(android.R.string.no, null) // No action on cancel
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }


    // --- Recording Logic ---

    /** Starts the audio recording process. */
    private void startRecording() {
        if (isRecording) { Log.w(TAG, "startRecording called while already recording."); return; }
        if (isPlaying) { Log.w(TAG, "startRecording called while playing. Stopping playback."); stopPlaying(); } // Stop playback first
        if (!hasAudioPermission()) { Log.e(TAG, "startRecording failed: Permission missing."); requestAudioPermission(); return; }
        if (getContext() == null) { Log.e(TAG, "startRecording failed: Context is null."); return; }

        Log.d(TAG, "Starting recording process...");
        int audioSource = MediaRecorder.AudioSource.MIC; // Standard microphone source
        // Consider trying UNPROCESSED if available and needed, but MIC is more standard
        // int audioSource = MediaRecorder.AudioSource.UNPROCESSED;

        try {
            // Validate buffer size again just before use
            if (BUFFER_SIZE <= 0) {
                Log.e(TAG, "Invalid buffer size: " + BUFFER_SIZE);
                updateStatusText(getString(R.string.status_error_recorder_config)); return;
            }

            // Explicit permission check before creating AudioRecord (belt-and-suspenders)
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission check failed unexpectedly right before creating AudioRecord.");
                updateStatusText(getString(R.string.status_error_permission_lost)); requestAudioPermission(); return;
            }

            // --- Initialize AudioRecord ---
            releaseAudioRecord(); // Release any previous instance first
            audioRecord = new AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed. State: " + audioRecord.getState());
                updateStatusText(getString(R.string.status_error_recorder_init));
                Toast.makeText(requireContext(), R.string.error_recorder_init_failed, Toast.LENGTH_SHORT).show();
                releaseAudioRecord(); return;
            }
            Log.d(TAG, "AudioRecord initialized successfully with source: " + audioSource);

            // --- Create File ---
            currentRecordingFile = new File(requireContext().getExternalFilesDir(null),
                    FILENAME_PREFIX + System.currentTimeMillis() + FILENAME_SUFFIX);
            Log.d(TAG, "Recording to file: " + currentRecordingFile.getAbsolutePath());

            // --- Start Recording & Background Thread ---
            isRecording = true; // Set flag BEFORE starting thread/recording
            audioRecord.startRecording();
            Log.d(TAG, "AudioRecord recording started.");

            updateStatusText(getString(R.string.status_recording)); // Update status text
            updateUiForCurrentState(); // Show Stop button, hide Record button

            // Start the background thread to write data
            executorService.execute(this::writeAudioDataToFile);
            Toast.makeText(requireContext(), R.string.recording_started, Toast.LENGTH_SHORT).show();

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException initializing/starting AudioRecord: " + e.getMessage(), e);
            updateStatusText(getString(R.string.status_error_permission_issue));
            Toast.makeText(requireContext(), R.string.error_recorder_permission, Toast.LENGTH_LONG).show();
            isRecording = false; releaseAudioRecord(); updateUiForCurrentState();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException initializing/starting AudioRecord: " + e.getMessage(), e);
            updateStatusText(getString(R.string.status_error_recorder_config));
            Toast.makeText(requireContext(), R.string.error_recorder_invalid_params, Toast.LENGTH_SHORT).show();
            isRecording = false; releaseAudioRecord(); updateUiForCurrentState();
        } catch (UnsupportedOperationException | IllegalStateException e) { // Catch specific runtime exceptions
            Log.e(TAG, e.getClass().getSimpleName() + " initializing/starting AudioRecord: " + e.getMessage(), e);
            updateStatusText(getString(R.string.status_error_recorder_unsupported));
            Toast.makeText(requireContext(), R.string.error_recorder_unsupported_config, Toast.LENGTH_LONG).show();
            isRecording = false; releaseAudioRecord(); updateUiForCurrentState();
        } catch (Exception e) { // Catch any other unexpected errors
            Log.e(TAG, "Unexpected error starting recording: " + e.getMessage(), e);
            updateStatusText(getString(R.string.status_error_starting_recorder));
            Toast.makeText(requireContext(), R.string.error_recorder_start_failed, Toast.LENGTH_SHORT).show();
            isRecording = false; releaseAudioRecord(); updateUiForCurrentState();
        }
    }

    /** Background task to read from AudioRecord and write to the current WAV file. */
    private void writeAudioDataToFile() {
        if (currentRecordingFile == null || audioRecord == null) {
            Log.e(TAG, "writeAudioDataToFile started with null file or AudioRecord!");
            mainHandler.post(() -> updateStatusText(getString(R.string.status_error_saving)));
            // Ensure recording state is reset if this somehow happens
            isRecording = false;
            mainHandler.post(this::onRecordingStoppedUpdateUi);
            return;
        }

        byte[] data = new byte[BUFFER_SIZE];
        FileOutputStream fos = null;
        long totalAudioLenBytes = 0;
        boolean writeSuccess = false;
        File fileBeingWritten = currentRecordingFile; // Use local ref in thread

        try {
            fos = new FileOutputStream(fileBeingWritten);
            writeWavHeader(fos, 0); // Write placeholder header

            Log.d(TAG, "Starting audio data read loop.");
            while (isRecording) { // Loop continues as long as isRecording is true
                if (audioRecord == null) { Log.w(TAG, "AudioRecord became null during write loop. Stopping."); break; }
                int read = audioRecord.read(data, 0, BUFFER_SIZE);
                if (read > 0) {
                    try {
                        fos.write(data, 0, read);
                        totalAudioLenBytes += read;
                    } catch (IOException e) {
                        Log.e(TAG, "IOException during file write chunk", e);
                        mainHandler.post(() -> updateStatusText(getString(R.string.status_error_saving)));
                        writeSuccess = false; // Mark failure on IO error
                        break; // Exit loop on write error
                    }
                } else if (read < 0) {
                    // Handle AudioRecord read errors
                    Log.e(TAG, "AudioRecord read error: " + read + ". Stopping recording thread.");
                    mainHandler.post(() -> updateStatusText(getString(R.string.status_error_reading_mic)));
                    writeSuccess = false; // Mark failure
                    break; // Exit loop on read error
                } // else read == 0, just continue looping
            } // End while(isRecording)

            Log.d(TAG, "Recording read loop finished. Total bytes read: " + totalAudioLenBytes);
            // If loop finished because isRecording became false, consider it a success *so far*
            // unless an IO error occurred earlier.
            if (writeSuccess || !isRecording) { // If no explicit failure and loop ended normally
                writeSuccess = true;
            }

        } catch (IOException e) {
            Log.e(TAG, "IOException setting up FileOutputStream: ", e);
            mainHandler.post(() -> updateStatusText(getString(R.string.status_error_saving)));
            writeSuccess = false; // Mark failure
        } finally {
            // --- Cleanup and Header Update ---
            if (fos != null) {
                try { fos.close(); } catch (IOException e) { Log.e(TAG,"Error closing FileOutputStream", e); writeSuccess=false;} // Mark failure if close fails
            }

            // Finalize header only if write likely succeeded AND there's actual audio data
            if (writeSuccess && totalAudioLenBytes > 0 && fileBeingWritten != null) {
                try {
                    updateWavHeader(fileBeingWritten, totalAudioLenBytes);
                    Log.i(TAG, "Recording saved successfully: " + fileBeingWritten.getName());
                    // Post actions that need to happen *after* successful save and header update
                    mainHandler.post(this::loadRecordingsList); // Refresh list to show new item
                    mainHandler.post(() -> {
                        if(isAdded() && getContext() != null) Toast.makeText(requireContext(), R.string.recording_saved_success, Toast.LENGTH_SHORT).show();
                        updateStatusText(getString(R.string.status_ready)); // Set status back to ready
                    });
                } catch (IOException e) {
                    Log.e(TAG, "IOException updating WAV header for " + fileBeingWritten.getName(), e);
                    mainHandler.post(() -> updateStatusText(getString(R.string.status_error_saving_header)));
                    writeSuccess = false; // Mark failure if header update fails
                }
            }

            // Delete the file if the write failed OR if no actual audio data was recorded
            // Use the potentially updated 'writeSuccess' value here
            if (!writeSuccess || totalAudioLenBytes <= 0) {
                Log.w(TAG, "Write failed (success=" + writeSuccess + ") or no data recorded (bytes=" + totalAudioLenBytes + "). Deleting file: " + (fileBeingWritten != null ? fileBeingWritten.getName():"null"));
                if (fileBeingWritten != null && fileBeingWritten.exists()) {
                    if (!fileBeingWritten.delete()) { // Log if delete fails
                        Log.w(TAG, "Failed to delete unsuccessful/empty recording file: " + fileBeingWritten.getName());
                    }
                }
                // Inform user if stopped but no data
                if (totalAudioLenBytes <= 0 && writeSuccess) {
                    mainHandler.post(() -> updateStatusText(getString(R.string.status_stopped_no_data)));
                }
            }

            // Reset the current file reference AFTER all operations are done for this recording
            currentRecordingFile = null;

            // Signal final UI update AFTER all file operations on this thread are complete
            mainHandler.post(this::onRecordingStoppedUpdateUi);

        } // End finally block
        Log.d(TAG, "Exiting writeAudioDataToFile thread.");
    } // End writeAudioDataToFile method

    /** Called when the user requests to stop recording. Signals the background thread. */
    private void stopRecording() {
        if (!isRecording) { Log.w(TAG, "stopRecording called but not recording."); return; }
        Log.d(TAG, "Stopping recording requested...");
        // Set the flag to false FIRST, so the background thread's loop condition fails
        isRecording = false;
        // Update UI immediately to show "Stopping..." and disable Stop button
        updateStatusText(getString(R.string.status_stopping));
        updateUiForCurrentState();
        // The background thread (writeAudioDataToFile) will handle releasing AudioRecord
        // and posting the final UI update via onRecordingStoppedUpdateUi.
    }

    /** Called from the background thread's finally block to perform final UI updates. */
    private void onRecordingStoppedUpdateUi() {
        Log.d(TAG, "onRecordingStoppedUpdateUi: Performing final UI update after recording process.");
        // Release the native AudioRecord resources - crucial!
        releaseAudioRecord();
        // Update UI to reflect idle state (Record button visible/enabled, Stop button gone)
        // Status text will be updated by the write thread based on success/failure/no data.
        updateUiForCurrentState();
    }

    // --- Playback Logic ---

    /** Starts playback of the specified audio file. */
    private void startPlaying(String filePath) {
        if (filePath == null || filePath.isEmpty()) { Log.e(TAG,"startPlaying: Invalid file path."); if(isAdded()) Toast.makeText(requireContext(), R.string.error_invalid_file, Toast.LENGTH_SHORT).show(); return; }
        if (isRecording) { Log.w(TAG, "startPlaying: Cannot play while recording."); if(isAdded()) Toast.makeText(requireContext(), R.string.error_stop_recording_to_play, Toast.LENGTH_SHORT).show(); return; }
        if (isPlaying) { stopPlaying(); } // Stop previous playback first
        if (getContext() == null) { Log.e(TAG, "startPlaying: Context is null."); return; }

        Log.d(TAG, "Starting playback for: " + filePath);
        releaseMediaPlayer(); // Ensure previous instance is released
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare(); // Prepare the player (can throw IOException)
            mediaPlayer.start();  // Start playback

            // Update state
            isPlaying = true;
            playingFilePath = filePath;

            // Update UI
            File playingFile = new File(filePath);
            updateStatusText(getString(R.string.status_playing, playingFile.getName()));
            updateUiForCurrentState(); // Reflect playing state in buttons
            // TODO: Notify adapter to highlight the playing item (advanced)

            // Set listeners
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Playback completed for: " + playingFile.getName());
                mainHandler.post(this::stopPlaying); // Stop and update UI on main thread
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer Error: what=" + what + ", extra=" + extra + " for " + playingFile.getName());
                mainHandler.post(() -> { // Update UI on main thread
                    updateStatusText(getString(R.string.status_error_playback));
                    if(isAdded()) Toast.makeText(requireContext(), R.string.error_playback_failed, Toast.LENGTH_SHORT).show();
                    stopPlaying(); // Cleanup on error
                });
                return true; // Error was handled
            });

        } catch (IOException | IllegalStateException | SecurityException e) { // Catch potential errors
            Log.e(TAG, "MediaPlayer setup/start failed for " + filePath, e);
            mainHandler.post(() -> updateStatusText(getString(R.string.status_error_preparing_playback)));
            if(isAdded()) Toast.makeText(requireContext(), R.string.error_preparing_playback_failed, Toast.LENGTH_LONG).show();
            releaseMediaPlayer(); // Clean up failed player
            updateUiForCurrentState();
        }
    }

    /** Stops the current audio playback and releases the MediaPlayer. */
    private void stopPlaying() {
        if (!isPlaying && mediaPlayer == null) return; // Already stopped
        Log.d(TAG, "Stopping playback.");
        releaseMediaPlayer(); // Handles stopping, resetting, releasing
        playingFilePath = null;
        updateUiForCurrentState(); // Update button states
        updateStatusText(getString(R.string.status_playback_stopped)); // Update status text
        // TODO: Notify adapter to remove playing indicator (advanced)
    }

    // --- Sharing Logic ---

    /** Shares the specified recording file using Android's sharing framework. */
    private void shareRecording(RecordingItem item) {
        if (item == null || item.getFilePath() == null || !isAdded() || getContext() == null) {
            Log.e(TAG,"Cannot share - invalid item or context");
            if(isAdded()) Toast.makeText(requireContext(), R.string.error_cannot_share_item, Toast.LENGTH_SHORT).show();
            return;
        }

        File fileToShare = new File(item.getFilePath());
        // Check if file exists and has more than just the header
        if (!fileToShare.exists() || fileToShare.length() <= 44) {
            Log.w(TAG,"Cannot share: File missing or empty - " + item.getFileName());
            if(isAdded()) Toast.makeText(requireContext(), R.string.error_share_file_missing, Toast.LENGTH_SHORT).show();
            updateStatusText(getString(R.string.status_error_share_file_missing));
            // Reload list to remove missing item potentially
            loadRecordingsList();
            return;
        }

        try {
            // Use FileProvider for secure sharing (essential for targetSdk >= 24)
            String authority = requireContext().getPackageName() + ".provider"; // Match authority in AndroidManifest.xml and filepaths.xml
            Uri fileUri = FileProvider.getUriForFile(requireContext(), authority, fileToShare);
            Log.d(TAG, "Sharing URI: " + fileUri + " for file: " + fileToShare.getPath());

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/wav"); // MIME type for WAV
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Grant permission to receiving app
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, item.getFileName())); // Optional subject

            // Create chooser to let user select app
            Intent chooser = Intent.createChooser(shareIntent, getString(R.string.share_title));

            // Verify that there's an app to handle the intent
            if (chooser.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(chooser);
                updateStatusText(getString(R.string.status_sharing));
            } else {
                Log.w(TAG,"No app found to handle sharing audio/wav intent.");
                if(isAdded()) Toast.makeText(requireContext(), R.string.error_no_app_for_sharing, Toast.LENGTH_SHORT).show();
                updateStatusText(getString(R.string.status_error_sharing_no_app));
            }
        } catch (IllegalArgumentException e) {
            // Catch errors from FileProvider (e.g., misconfigured paths)
            Log.e(TAG, "FileProvider Error sharing " + item.getFileName() + ": Check filepaths.xml and authority.", e);
            if(isAdded()) Toast.makeText(requireContext(), R.string.error_sharing_setup_issue, Toast.LENGTH_LONG).show();
            updateStatusText(getString(R.string.status_error_sharing_failed));
        } catch (Exception e) { // Catch any other exceptions during sharing setup/start
            Log.e(TAG, "Error initiating sharing for " + item.getFileName(), e);
            if(isAdded()) Toast.makeText(requireContext(), R.string.error_sharing_failed, Toast.LENGTH_SHORT).show();
            updateStatusText(getString(R.string.status_error_sharing_failed));
        }
    }

    // --- Adapter Click Listener Implementation (OnItemActionListener) ---

    @Override
    public void onPlayClick(RecordingItem item, int position) {
        Log.d(TAG, "Play clicked for pos " + position + ": " + item.getFileName());
        // Toggle play/stop for the clicked item
        if (isPlaying && playingFilePath != null && playingFilePath.equals(item.getFilePath())) {
            stopPlaying(); // Stop if this item is currently playing
        } else {
            startPlaying(item.getFilePath()); // Start playing this item
        }
    }

    @Override
    public void onShareClick(RecordingItem item, int position) {
        Log.d(TAG, "Share clicked for pos " + position + ": " + item.getFileName());
        shareRecording(item); // Call the sharing method
    }

    @Override
    public void onDeleteClick(RecordingItem item, int position) {
        Log.d(TAG, "Delete clicked for pos " + position + ": " + item.getFileName());
        // Stop playback if deleting the currently playing item
        if (isPlaying && playingFilePath != null && playingFilePath.equals(item.getFilePath())) {
            stopPlaying();
        }
        deleteRecording(item, position); // Call the (corrected) delete method
    }

    /** Handles long click on a list item, initiating the rename process. */
    @Override
    public void onItemLongClick(RecordingItem item, int position) {
        Log.d(TAG, "Long clicked on pos " + position + ": " + item.getFileName());
        // Stop playback if renaming the currently playing item
        if (isPlaying && playingFilePath != null && playingFilePath.equals(item.getFilePath())) {
            stopPlaying();
        }
        showRenameDialog(item, position); // Show the rename input dialog
    }

    // --- Rename Functionality ---

    /** Displays an AlertDialog to get the new filename from the user. */
    private void showRenameDialog(final RecordingItem item, final int position) {
        if (!isAdded() || getContext() == null) return; // Check fragment state

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.dialog_rename_title);

        // Inflate custom layout for the dialog (requires res/layout/dialog_rename.xml)
        final View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_rename, null);
        final EditText input = dialogView.findViewById(R.id.edit_text_new_name); // Get EditText from dialog layout

        // Pre-fill with current name without the ".wav" extension
        String currentName = item.getFileName();
        int extensionIndex = currentName.toLowerCase().lastIndexOf(FILENAME_SUFFIX);
        if (extensionIndex > 0) { // Ensure suffix is found and not at the beginning
            input.setText(currentName.substring(0, extensionIndex));
        } else {
            input.setText(currentName); // Fallback if no extension or weird format
        }
        input.requestFocus(); // Set focus to the input field
        input.setSelection(input.getText().length()); // Move cursor to end
        builder.setView(dialogView);

        // Set up the dialog buttons
        builder.setPositiveButton(R.string.dialog_rename_button, (dialog, which) -> {
            String newBaseName = input.getText().toString().trim();
            if (validateFilename(newBaseName)) {
                // Perform rename on background thread if it involves file IO
                executorService.execute(() -> performRename(item, position, newBaseName));
            } else {
                // Show validation error on main thread
                mainHandler.post(()-> {if(isAdded()) Toast.makeText(getContext(), R.string.error_invalid_filename, Toast.LENGTH_SHORT).show();});
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

     /* Reminder: Create res/layout/dialog_rename.xml:
     <?xml version="1.0" encoding="utf-8"?>
     <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
         android:orientation="vertical"
         android:layout_width="match_parent"
         android:layout_height="wrap_content"
         android:paddingStart="24dp"
         android:paddingEnd="24dp"
         android:paddingTop="20dp"
         android:paddingBottom="10dp">

         <EditText
             android:id="@+id/edit_text_new_name"
             android:layout_width="match_parent"
             android:layout_height="wrap_content"
             android:hint="@string/hint_enter_new_name"
             android:inputType="text"
             android:maxLines="1"/>
     </LinearLayout>
     */

    /** Validates the proposed base filename (without extension). */
    private boolean validateFilename(String baseName) {
        if (baseName == null || baseName.isEmpty()) {
            Log.w(TAG, "Validation failed: Filename is empty.");
            return false;
        }
        // Check for invalid characters using the pre-compiled pattern
        if (INVALID_FILENAME_CHARS.matcher(baseName).find()) {
            Log.w(TAG, "Validation failed: Invalid characters found in proposed filename: " + baseName);
            return false;
        }
        // Check for names that might be problematic for filesystems (e.g., '.', '..')
        if (baseName.equals(".") || baseName.equals("..")) {
            Log.w(TAG, "Validation failed: Filename cannot be '.' or '..'.");
            return false;
        }
        // Add any other checks (e.g., maximum length) if needed
        return true;
    }

    /** Performs the actual file rename operation and updates the adapter. */
    private void performRename(final RecordingItem item, final int position, final String newBaseName) {
        // Run file IO on background thread (already called via executorService)
        if (item == null || item.getFilePath() == null || newBaseName == null || newBaseName.isEmpty() || getContext() == null) {
            Log.e(TAG,"performRename: Invalid input."); return;
        }

        File oldFile = new File(item.getFilePath());
        File recordingsDir = oldFile.getParentFile(); // Get the directory
        if (recordingsDir == null) {
            Log.e(TAG,"performRename: Cannot get parent directory for " + oldFile.getPath());
            mainHandler.post(() -> { if(isAdded()) Toast.makeText(getContext(), R.string.error_accessing_storage, Toast.LENGTH_SHORT).show(); });
            return;
        }

        final String newFileName = newBaseName + FILENAME_SUFFIX; // Add the extension back
        final File newFile = new File(recordingsDir, newFileName);

        // Prevent renaming to the exact same name (no-op)
        if (newFile.getAbsolutePath().equals(oldFile.getAbsolutePath())) {
            Log.w(TAG, "performRename: New name is the same as the old name. No action taken.");
            // Optionally inform user with a Toast on main thread
            // mainHandler.post(() -> Toast.makeText(getContext(), "Name is unchanged.", Toast.LENGTH_SHORT).show());
            return;
        }

        // Check if a file with the new name already exists
        if (newFile.exists()) {
            Log.w(TAG,"Rename failed: File already exists - " + newFileName);
            mainHandler.post(() -> { if(isAdded()) Toast.makeText(getContext(), R.string.error_rename_file_exists, Toast.LENGTH_SHORT).show(); });
            return;
        }

        Log.d(TAG, "Attempting rename on background thread: '" + oldFile.getName() + "' -> '" + newFileName + "'");
        boolean success = false;
        try {
            success = oldFile.renameTo(newFile); // The actual rename operation
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException during rename: ", se);
        } catch (Exception e) { // Catch any other IO errors
            Log.e(TAG, "Exception during rename: ", e);
        }

        // --- Post-Rename Actions (on main thread) ---
        final boolean renameSuccess = success; // Final variable for lambda
        mainHandler.post(() -> {
            if (!isAdded()) return; // Check fragment state

            if (renameSuccess) {
                Log.i(TAG, "Rename successful for " + newFileName);
                // Double-check position and item validity before updating list/adapter
                if (position >= 0 && position < recordingItemsList.size() && recordingItemsList.get(position).equals(item)) { // Use .equals
                    // Update the RecordingItem object IN the list
                    recordingItemsList.get(position).updateNameAndPath(newFileName, newFile.getAbsolutePath());

                    // Update the display formatting if needed (duration shouldn't change)
                    // String newFormattedDate = ... ;
                    // recordingItemsList.get(position).setFormattedDate(newFormattedDate);

                    if (recordingsAdapter != null) {
                        recordingsAdapter.notifyItemChanged(position); // Notify adapter about the change
                    }
                    Toast.makeText(getContext(), R.string.rename_success, Toast.LENGTH_SHORT).show();
                } else {
                    // Should not happen if list isn't modified elsewhere, but handle defensively
                    Log.w(TAG,"Position/Item mismatch during rename UI update. Reloading list.");
                    loadRecordingsList(); // Fallback: reload if something went wrong
                }
            } else {
                Log.e(TAG, "Rename failed for '" + oldFile.getName() + "' to '" + newFileName + "'");
                Toast.makeText(getContext(), R.string.error_rename_failed, Toast.LENGTH_SHORT).show();
            }
        }); // End mainHandler.post
    }

    // --- Helper Methods ---

    /** Safely updates the status TextView on the UI thread. */
    private void updateStatusText(final String status) {
        mainHandler.post(() -> {
            if (statusTextView != null && isAdded()) { // Check if fragment is still attached
                statusTextView.setText(status);
            }
        });
        // Log immediately for debugging, even if UI update is posted
        Log.d(TAG, "UI Status Updated: " + status);
    }

    /** Centralized UI update for main Record/Stop buttons based on current state */
    private void updateUiForCurrentState() {
        mainHandler.post(() -> {
            if (!isAdded()) { Log.w(TAG,"updateUiForCurrentState skipped: Fragment not attached."); return; }
            boolean hasPerm = hasAudioPermission();

            // Determine intended states
            boolean canRecord = hasPerm && !isRecording && !isPlaying;
            boolean showStop = isRecording;
            boolean showRecord = !isRecording;

            // Record Button
            if (recordButton != null) {
                recordButton.setEnabled(canRecord);
                recordButton.setVisibility(showRecord ? View.VISIBLE : View.GONE);
                // Optionally change icon or text based on state if needed (e.g., show pause icon if playing)
            } else { Log.w(TAG, "updateUi: recordButton is null!"); }

            // Stop Button
            if (stopButton != null) {
                // Stop button is only relevant during recording in this setup
                stopButton.setEnabled(isRecording); // Enable only when actually recording
                stopButton.setVisibility(showStop ? View.VISIBLE : View.GONE);
            } else { Log.w(TAG, "updateUi: stopButton is null!"); }

            // Update Status Text based on priority (only if NOT actively recording)
            if (!isRecording) { // Let recording status take priority
                if (isPlaying) {
                    // Status updated in startPlaying/stopPlaying
                } else {
                    // Idle state: Ready if permission granted, otherwise prompt for permission
                    updateStatusText(hasPerm ? getString(R.string.status_ready) : getString(R.string.permission_needed_to_record));
                }
            }

            Log.d(TAG, "UI Button States updated: hasPerm=" + hasPerm + ", isPlaying=" + isPlaying + ", isRecording=" + isRecording);
        });
    }

    /** Safely releases the AudioRecord instance */
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            Log.d(TAG, "Attempting to release AudioRecord...");
            AudioRecord recordToRelease = audioRecord;
            audioRecord = null; // Nullify the main reference first
            try {
                // Check state before stopping/releasing to avoid IllegalStateException
                if (recordToRelease.getState() == AudioRecord.STATE_INITIALIZED) {
                    if (recordToRelease.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        try { recordToRelease.stop(); Log.d(TAG,"AudioRecord stopped in release helper."); }
                        catch (IllegalStateException e) { Log.e(TAG, "IllegalStateException stopping AudioRecord in release: ", e); }
                    }
                    recordToRelease.release(); // Release native resources
                    Log.d(TAG,"AudioRecord released via helper.");
                } else { Log.w(TAG, "AudioRecord was not initialized, skipping stop/release steps."); }
            } catch (Exception e) { // Catch any other unexpected errors during release
                Log.e(TAG, "Exception releasing AudioRecord: ", e);
            }
        }
    }

    /** Safely stops, resets, and releases the MediaPlayer instance */
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            Log.d(TAG, "Attempting to release MediaPlayer...");
            MediaPlayer playerToRelease = mediaPlayer;
            mediaPlayer = null; // Nullify first
            isPlaying = false; // Ensure state flag is reset
            // playingFilePath is reset in stopPlaying or startPlaying
            try {
                // It's generally safer to check isPlaying before stop/reset
                if (playerToRelease.isPlaying()) {
                    playerToRelease.stop();
                }
                playerToRelease.reset(); // Reset state machine
                playerToRelease.release(); // Release native resources
                Log.d(TAG,"MediaPlayer released via helper.");
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException releasing MediaPlayer (likely already released or in wrong state): ", e);
            } catch (Exception e) {
                Log.e(TAG, "Exception releasing MediaPlayer: ", e);
            }
        }
        // Ensure state flags are reset even if mediaPlayer was already null
        isPlaying = false;
        // playingFilePath = null; // Resetting here might clear it too early if called during startPlaying error handling
    }

    /** Shuts down the executor service gracefully. */
    private void shutdownExecutorService() {
        if (executorService != null && !executorService.isShutdown()) {
            Log.d(TAG, "Shutting down ExecutorService...");
            executorService.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow(); // Cancel currently executing tasks
                    Log.w(TAG, "Executor service did not terminate gracefully, forcing shutdown.");
                    // Re-await termination after forcing
                    if (!executorService.awaitTermination(1, TimeUnit.SECONDS))
                        Log.e(TAG, "Executor service did not terminate even after shutdownNow().");
                } else { Log.d(TAG, "Executor service terminated gracefully."); }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                executorService.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
                Log.e(TAG,"Executor shutdown interrupted.", ie);
            }
        }
    }


    // --- Corrected WAV File Helper Methods ---
    // (Keep these as they were - essential for playable WAV files)

    /** Writes a valid 44-byte WAV header placeholder */
    private void writeWavHeader(OutputStream out, long totalAudioLen) throws IOException {
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = SAMPLE_RATE;
        int channels = (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2);
        int bitsPerSample = (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8);
        long byteRate = longSampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F'; // RIFF chunk descriptor
        header[4] = (byte) (totalDataLen & 0xff); // file size - 8
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E'; // WAVE format
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' '; // 'fmt ' subchunk
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // 16 for PCM size
        header[20] = 1; header[21] = 0; // Audio format 1=PCM
        header[22] = (byte) channels; header[23] = 0; // number of channels
        header[24] = (byte) (longSampleRate & 0xff); // sample rate
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff); // byte rate = sampleRate * channels * bitsPerSample/8
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign; header[33] = 0; // block align = channels * bitsPerSample/8
        header[34] = (byte) bitsPerSample; header[35] = 0; // bits per sample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a'; // 'data' subchunk
        header[40] = (byte) (totalAudioLen & 0xff); // subchunk 2 size = NumSamples * channels * bitsPerSample/8
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
        Log.d(TAG, "WAV header placeholder written.");
    }

    /** Updates the WAV header with correct file sizes after recording is finished */
    private void updateWavHeader(File file, long totalAudioLenBytes) throws IOException {
        if (file == null || !file.exists() || totalAudioLenBytes < 0) {
            throw new IOException("Invalid file or audio length for header update.");
        }
        // Prepare the 8 bytes to update: 4 for RIFF size, 4 for data chunk size
        long totalDataLen = totalAudioLenBytes + 36; // RIFF chunk size (file size - 8)
        byte[] headerUpdates = new byte[8];
        // RIFF chunk size (offset 4)
        headerUpdates[0] = (byte) (totalDataLen & 0xff);
        headerUpdates[1] = (byte) ((totalDataLen >> 8) & 0xff);
        headerUpdates[2] = (byte) ((totalDataLen >> 16) & 0xff);
        headerUpdates[3] = (byte) ((totalDataLen >> 24) & 0xff);
        // Data chunk size (offset 40)
        headerUpdates[4] = (byte) (totalAudioLenBytes & 0xff);
        headerUpdates[5] = (byte) ((totalAudioLenBytes >> 8) & 0xff);
        headerUpdates[6] = (byte) ((totalAudioLenBytes >> 16) & 0xff);
        headerUpdates[7] = (byte) ((totalAudioLenBytes >> 24) & 0xff);

        RandomAccessFile wavFile = null;
        try {
            wavFile = new RandomAccessFile(file, "rw"); // Open for read-write
            // Seek and write RIFF chunk size
            wavFile.seek(4);
            wavFile.write(headerUpdates, 0, 4);
            // Seek and write data chunk size
            wavFile.seek(40);
            wavFile.write(headerUpdates, 4, 4);
            Log.d(TAG, "WAV header updated successfully for " + file.getName() + " (Data Size: " + totalAudioLenBytes + ")");
        } finally {
            // Ensure the file is closed
            if (wavFile != null) {
                try { wavFile.close(); } catch (IOException e) { Log.e(TAG, "Error closing RandomAccessFile after header update", e); }
            }
        }
    }
    // --- END WAV ---

} // End of RecordYourselfFragment class