package com.example.vocalharmony.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder; // Keep for AudioSource constants
import android.net.Uri;
// import android.os.Build; // No longer needed for SDK check here
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Needed for onCreate
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordYourselfFragment extends Fragment {

    private static final String TAG = "RecordYourselfFragment";
    // Use REQUEST_RECORD_AUDIO_PERMISSION consistently if defined elsewhere, or just use ActivityResultLauncher
    // private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200; // Not needed with ActivityResultLauncher
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = Math.max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            2048 // Ensure a reasonable minimum buffer size
    );

    private AudioRecord audioRecord;
    private MediaPlayer mediaPlayer;
    private String audioFilePath;
    private volatile boolean isRecording = false;
    private boolean isPlaying = false;

    // UI Elements
    private Button buttonRecord;
    private Button buttonPlay;
    private Button buttonStop;
    private Button buttonShare;
    private TextView textRecordingStatus;

    // Threading, Timing, and Permissions
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Made final
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // Made final
    private File recordingFile;
    private long recordingStartTime;
    private ActivityResultLauncher<String> requestPermissionLauncher; // For new permission handling

    // --- Fragment Lifecycle & Permission Setup ---

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the ActivityResultLauncher for permission requests
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // Permission is granted.
                        Toast.makeText(requireContext(), "Permission granted. Ready to record.", Toast.LENGTH_SHORT).show();
                        updateStatusText(getString(R.string.status_ready)); // Use string resource
                        if(buttonRecord != null) buttonRecord.setEnabled(true); // Ensure button is enabled
                    } else {
                        // Permission denied.
                        Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_SHORT).show();
                        updateStatusText("Permission denied. Cannot record.");
                        if(buttonRecord != null) buttonRecord.setEnabled(false); // Disable record button
                    }
                });
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_record_yourself, container, false);

        buttonRecord = root.findViewById(R.id.button_record);
        buttonPlay = root.findViewById(R.id.button_play);
        buttonStop = root.findViewById(R.id.button_stop);
        buttonShare = root.findViewById(R.id.button_share);
        textRecordingStatus = root.findViewById(R.id.text_recording_status);

        // --- Button OnClickListeners ---
        buttonRecord.setOnClickListener(v -> {
            if (hasAudioPermission()) { // Use updated check method
                if (!isRecording) {
                    startRecording();
                } else {
                    stopRecording();
                }
            } else {
                // Permission is not granted, request it
                requestAudioPermission();
            }
        });

        buttonPlay.setOnClickListener(v -> {
            if (!isRecording && audioFilePath != null && !isPlaying) {
                startPlaying();
            } else if (isPlaying) {
                stopPlaying();
            } else {
                Toast.makeText(requireContext(), R.string.status_no_file, Toast.LENGTH_SHORT).show(); // Use string resource
            }
        });

        buttonStop.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            }
            if (isPlaying) {
                stopPlaying();
            }
        });

        buttonShare.setOnClickListener(v -> {
            if (audioFilePath != null && recordingFile != null && recordingFile.exists()) {
                shareRecording();
            } else {
                Toast.makeText(requireContext(), R.string.status_no_file, Toast.LENGTH_SHORT).show(); // Use string resource
            }
        });

        // --- Initial UI State & Permission Check on View Creation ---
        buttonPlay.setEnabled(false);
        buttonStop.setVisibility(View.GONE);
        buttonShare.setEnabled(false);
        if (!hasAudioPermission()) {
            buttonRecord.setEnabled(false); // Disable if no permission initially
            updateStatusText("Permission needed to record.");
        } else {
            buttonRecord.setEnabled(true);
            updateStatusText(getString(R.string.status_ready)); // Use string resource
        }

        return root;
    }

    // --- New Permission Handling Methods ---

    private boolean hasAudioPermission() {
        // Added null check for context safety
        if (getContext() == null) return false;
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        // Check if we should show an explanation (optional but good UX)
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // Example: Show a simple Toast. A dialog is better for complex explanations.
            Toast.makeText(requireContext(), "Recording permission allows the app to capture audio.", Toast.LENGTH_LONG).show();
        }
        // Launch the permission request
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    // --- Recording Logic ---

    private void startRecording() {
        if (isRecording) return;

        // Assume minSdkVersion >= 24 based on previous lint warning
        Log.d(TAG, "Using UNPROCESSED audio source (requires API 24+).");
        int audioSource = MediaRecorder.AudioSource.UNPROCESSED;
        String sourceUsed = "UNPROCESSED";

        // Double-check permission just before creating AudioRecord
        if (!hasAudioPermission()) {
            updateStatusText("Error: Permission missing.");
            Toast.makeText(requireContext(), "Cannot record without permission.", Toast.LENGTH_SHORT).show();
            return; // Don't proceed
        }

        try {
            Log.d(TAG, "Buffer size: " + BUFFER_SIZE);
            if (BUFFER_SIZE <= 0) {
                Log.e(TAG, "Invalid buffer size calculated: " + BUFFER_SIZE);
                updateStatusText("Error: Invalid buffer size.");
                Toast.makeText(requireContext(), "Audio recorder parameter error.", Toast.LENGTH_SHORT).show();
                return;
            }

            audioRecord = new AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed. State: " + audioRecord.getState());
                updateStatusText("Error initializing recorder.");
                Toast.makeText(requireContext(), "Audio recorder failed to initialize.", Toast.LENGTH_SHORT).show();
                releaseAudioRecord(); // Use helper to release safely
                return;
            }
            Log.d(TAG, "AudioRecord initialized successfully with source: " + sourceUsed);

            recordingFile = new File(requireContext().getExternalFilesDir(null), "VocalHarmony_" + System.currentTimeMillis() + ".wav");
            audioFilePath = recordingFile.getAbsolutePath();
            Log.d(TAG, "Recording to: " + audioFilePath);

            recordingStartTime = System.currentTimeMillis();
            isRecording = true; // Set flag BEFORE starting thread/recording
            audioRecord.startRecording();
            Log.d(TAG, "AudioRecord recording started.");

            updateStatusText("Initializing save...");
            executorService.execute(this::writeAudioDataToFile); // Start background writing

            mainHandler.post(() -> { // Update UI on main thread
                // *** THIS IS THE CORRECTED LINE ***
                buttonRecord.setText(R.string.stop_audio_recording); // Use the renamed string ID
                // *** END OF CORRECTION ***

                buttonPlay.setEnabled(false);
                buttonShare.setEnabled(false);
                buttonStop.setVisibility(View.VISIBLE);
                mainHandler.post(updateTimerRunnable); // Start timer updates
            });
            Toast.makeText(requireContext(), R.string.recording_started, Toast.LENGTH_SHORT).show();

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException initializing AudioRecord: " + e.getMessage(), e);
            updateStatusText("Error: Permission issue.");
            Toast.makeText(requireContext(), "Permission issue starting recorder.", Toast.LENGTH_LONG).show();
            isRecording = false; // Reset flag
            releaseAudioRecord();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException initializing AudioRecord: " + e.getMessage(), e);
            updateStatusText("Error: Invalid parameters.");
            Toast.makeText(requireContext(), "Invalid recorder parameters.", Toast.LENGTH_SHORT).show();
            isRecording = false;
            releaseAudioRecord();
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "UnsupportedOperationException initializing AudioRecord: " + e.getMessage(), e);
            updateStatusText("Error: Config not supported.");
            Toast.makeText(requireContext(), "Audio configuration not supported on this device.", Toast.LENGTH_LONG).show();
            isRecording = false;
            releaseAudioRecord();
        }
    }

    // --- Runnable to update recording timer display ---
    private final Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                long elapsedMillis = System.currentTimeMillis() - recordingStartTime;
                long seconds = (elapsedMillis / 1000) % 60;
                long minutes = (elapsedMillis / (1000 * 60)) % 60;
                updateStatusText(String.format(Locale.getDefault(), getString(R.string.status_recording), minutes, seconds)); // Use string resource
                mainHandler.postDelayed(this, 1000); // Post again
            }
        }
    };

    // --- Background task for writing audio data ---
    private void writeAudioDataToFile() {
        byte[] data = new byte[BUFFER_SIZE];
        FileOutputStream fos = null;
        long totalAudioLenBytes = 0;

        try {
            fos = new FileOutputStream(recordingFile);
            writeWavHeader(fos, 0); // Write placeholder header

            Log.d(TAG, "Starting audio data read loop.");
            while (isRecording) {
                if (audioRecord == null) break;
                int read = audioRecord.read(data, 0, BUFFER_SIZE);
                if (read > 0) {
                    try {
                        fos.write(data, 0, read);
                        totalAudioLenBytes += read;
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to file stream", e);
                        mainHandler.post(() -> updateStatusText(getString(R.string.status_error_saving))); // Use string resource
                        break;
                    }
                } else if (read < 0) { // Check for errors explicitly
                    Log.e(TAG, "AudioRecord read error code: " + read);
                    final int errorRead = read; // Final for lambda
                    mainHandler.post(() -> updateStatusText(getString(R.string.status_error_recording) + " (Code: " + errorRead +")")); // Use string resource
                    break; // Stop on read error
                }
            }
            Log.d(TAG, "Audio data read loop finished. Total bytes written: " + totalAudioLenBytes);

            // Ensure stream is flushed and closed before updating header
            fos.flush();
            fos.getChannel().force(true); // Attempt to sync metadata
            fos.close(); // Close stream
            fos = null; // Nullify to prevent closing again in finally

            // Update header only if data was written
            if (totalAudioLenBytes > 0) {
                updateWavHeader(recordingFile, totalAudioLenBytes);
                mainHandler.post(() -> {
                    // Check recordingFile is not null before using getName()
                    if(recordingFile != null) {
                        updateStatusText(String.format(getString(R.string.status_saved), recordingFile.getName())); // Use string resource
                    } else {
                        updateStatusText(getString(R.string.status_saved).replace(": %s", "")); // Generic saved message
                    }
                    buttonShare.setEnabled(true);
                });
            } else {
                Log.w(TAG, "No audio data written. Deleting empty file.");
                if (recordingFile != null) {
                    if (!recordingFile.delete()) { Log.w(TAG, "Failed to delete empty file."); }
                    recordingFile = null; // Nullify after deletion attempt
                }
                audioFilePath = null; // Clear path
                mainHandler.post(() -> {
                    updateStatusText("Recording stopped (no data)");
                    buttonShare.setEnabled(false);
                });
            }

        } catch (IOException e) {
            Log.e(TAG, "IOException during file writing setup or closing: ", e);
            mainHandler.post(() -> updateStatusText(getString(R.string.status_error_saving))); // Use string resource
            if (recordingFile != null && recordingFile.exists()) {
                if (!recordingFile.delete()) { Log.w(TAG, "Failed to delete potentially corrupt file."); }
                recordingFile = null; // Nullify after deletion attempt
            }
            audioFilePath = null;
        } finally {
            if (fos != null) { // If stream wasn't closed successfully in try block
                try { fos.close(); } catch (IOException e) { Log.e(TAG, "Error closing fos in finally", e); }
            }
            // If stopRecording() was called, trigger UI updates after file operations
            if (!isRecording) {
                mainHandler.post(this::onRecordingStoppedUI);
            }
        }
        Log.d(TAG, "Exiting writeAudioDataToFile thread.");
    }


    // --- Stopping Recording ---
    private void stopRecording() {
        if (!isRecording || audioRecord == null) return;
        Log.d(TAG, "Stopping recording...");

        isRecording = false; // Signal writing thread to stop FIRST

        mainHandler.removeCallbacks(updateTimerRunnable); // Stop timer updates
        updateStatusText(getString(R.string.status_stopping)); // Update UI immediately

        // Writing thread will handle actual stopping of AudioRecord and final UI updates via onRecordingStoppedUI
    }

    // --- UI updates after recording thread finishes ---
    private void onRecordingStoppedUI() {
        Log.d(TAG, "onRecordingStoppedUI called.");
        releaseAudioRecord(); // Release AudioRecord resources

        // Update UI elements
        buttonRecord.setText(R.string.record_button); // Reset button text
        buttonRecord.setEnabled(true); // Re-enable record button
        buttonPlay.setEnabled(audioFilePath != null); // Enable play only if a valid file was saved
        buttonShare.setEnabled(audioFilePath != null && recordingFile != null && recordingFile.exists() && recordingFile.length() > 44); // Check size > header
        buttonStop.setVisibility(View.GONE); // Hide stop button

        // Set final status message if not already set by write thread
        if (textRecordingStatus != null) {
            String currentStatus = textRecordingStatus.getText().toString();
            // Avoid overwriting specific error/saved messages
            if (currentStatus.equals(getString(R.string.status_stopping)) || currentStatus.startsWith("Recording...")) {
                if (audioFilePath != null) {
                    updateStatusText("Stopped. Ready to play/share.");
                } else {
                    updateStatusText(getString(R.string.status_ready));
                }
            }
        }

        Toast.makeText(requireContext(), R.string.recording_stopped, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Recording stopped and resources released (UI updated).");
    }


    // --- Playback Logic ---
    private void startPlaying() {
        if (isPlaying || audioFilePath == null) return;
        if (isRecording) stopRecording(); // Stop recording first if active

        mediaPlayer = new MediaPlayer();
        try {
            Log.d(TAG, "Starting playback for: " + audioFilePath);
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;

            buttonPlay.setText(R.string.stop_button);
            buttonRecord.setEnabled(false);
            buttonShare.setEnabled(false);
            buttonStop.setVisibility(View.VISIBLE);
            updateStatusText(String.format(getString(R.string.status_playing), (recordingFile != null ? recordingFile.getName() : "Recording"))); // Use string resource

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Playback completed.");
                stopPlaying();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer Error: what=" + what + ", extra=" + extra);
                updateStatusText(getString(R.string.status_error_playback)); // Use string resource
                stopPlaying();
                Toast.makeText(requireContext(), "Error playing audio.", Toast.LENGTH_SHORT).show();
                return true;
            });

            Toast.makeText(requireContext(), R.string.playback_started, Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e(TAG, "MediaPlayer prepare() failed for " + audioFilePath, e);
            updateStatusText("Error preparing playback.");
            Toast.makeText(requireContext(), "Error preparing playback: " + e.getMessage(), Toast.LENGTH_LONG).show();
            releaseMediaPlayer();
        } catch (IllegalArgumentException | SecurityException | IllegalStateException e){
            Log.e(TAG, "MediaPlayer start error: ", e);
            updateStatusText("Error starting playback.");
            Toast.makeText(requireContext(), "Error starting playback.", Toast.LENGTH_SHORT).show();
            releaseMediaPlayer();
        }
    }

    private void stopPlaying() {
        releaseMediaPlayer(); // Use helper method

        // Update UI
        isPlaying = false; // Ensure flag is false
        buttonPlay.setText(R.string.play_button);
        buttonPlay.setEnabled(audioFilePath != null);
        buttonRecord.setEnabled(true);
        buttonShare.setEnabled(audioFilePath != null && recordingFile != null && recordingFile.exists() && recordingFile.length() > 44);
        buttonStop.setVisibility(View.GONE);

        if (audioFilePath != null) {
            updateStatusText("Stopped playback. Ready.");
        } else {
            updateStatusText(getString(R.string.status_ready));
        }
        Toast.makeText(requireContext(), R.string.playback_stopped, Toast.LENGTH_SHORT).show();
    }

    // --- Sharing Logic ---
    private void shareRecording() {
        if (recordingFile == null || !recordingFile.exists() || recordingFile.length() <= 44) {
            Toast.makeText(requireContext(), "Valid recording not found.", Toast.LENGTH_SHORT).show();
            updateStatusText("Cannot share: File missing/empty.");
            return;
        }

        try {
            // Ensure context is not null before proceeding
            if (getContext() == null) {
                Log.e(TAG,"Context is null, cannot get package name for FileProvider.");
                Toast.makeText(requireContext(), "Cannot share recording (Internal error).", Toast.LENGTH_SHORT).show();
                return;
            }
            String authority = requireContext().getPackageName() + ".provider";
            Uri fileUri = FileProvider.getUriForFile(requireContext(), authority, recordingFile);
            Log.d(TAG, "Sharing URI: " + fileUri);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/wav");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "VocalHarmony Recording");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Listen to this recording from VocalHarmony: " + recordingFile.getName());

            Intent chooser = Intent.createChooser(shareIntent, "Share Recording via");
            // Check if there's an app to handle the intent
            if (getContext() != null && chooser.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(chooser);
                updateStatusText("Sharing...");
            } else {
                Toast.makeText(requireContext(), "No app found to handle sharing audio.", Toast.LENGTH_SHORT).show();
                updateStatusText("Sharing failed: No app found.");
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "FileProvider error sharing " + (recordingFile != null ? recordingFile.getName() : "null file") + ": " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Sharing setup error. Check FileProvider config.", Toast.LENGTH_LONG).show();
            updateStatusText("Error: Sharing setup issue.");
        } catch (Exception e) { // Catch other potential errors like ActivityNotFoundException
            Log.e(TAG, "Error creating/starting share intent for " + (recordingFile != null ? recordingFile.getName() : "null file") + ": " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Could not share recording.", Toast.LENGTH_SHORT).show();
            updateStatusText("Error: Sharing failed.");
        }
    }

    // --- Helper Methods ---

    private void updateStatusText(final String status) {
        // Use post for safety, even if already on main thread sometimes
        mainHandler.post(() -> {
            // Check if view is still valid
            if (textRecordingStatus != null && isAdded()) {
                textRecordingStatus.setText(status);
            }
        });
        Log.d(TAG, "Status Updated: " + status);
    }

    // Helper to release AudioRecord resources safely
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            // Use a temporary variable to avoid race condition if another thread nullifies it
            AudioRecord recordToRelease = audioRecord;
            audioRecord = null; // Nullify the main reference first
            try {
                // Check state before stopping
                if (recordToRelease.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recordToRelease.stop();
                    Log.d(TAG,"AudioRecord stopped in release helper.");
                }
                recordToRelease.release(); // Release native resources
                Log.d(TAG,"AudioRecord released in release helper.");
            } catch (Exception e) {
                Log.e(TAG, "Exception releasing AudioRecord: ", e);
            }
            // No need to nullify again, already done
        }
    }

    // Helper to release MediaPlayer resources safely
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            MediaPlayer playerToRelease = mediaPlayer;
            mediaPlayer = null; // Nullify first
            isPlaying = false; // Ensure flag is reset
            try {
                // Check state before stopping/resetting if possible
                if (playerToRelease.isPlaying()) {
                    playerToRelease.stop();
                }
                playerToRelease.reset(); // Good practice before release
                playerToRelease.release(); // Release native resources
                Log.d(TAG,"MediaPlayer released in release helper.");
            } catch (Exception e) {
                Log.e(TAG, "Exception releasing MediaPlayer: ", e);
            }
        }
        // Ensure flag is false even if mediaPlayer was already null
        isPlaying = false;
    }


    // --- Fragment Lifecycle Cleanup ---

    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) {
            Log.w(TAG, "onPause: Fragment paused during recording. Stopping recording.");
            stopRecording();
        }
        if (isPlaying) {
            Log.w(TAG, "onPause: Fragment paused during playback. Stopping playback.");
            stopPlaying();
        }
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG,"onDestroyView: Removing handler callbacks.");
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        // Nullify view references to allow garbage collection
        buttonRecord = null;
        buttonPlay = null;
        buttonStop = null;
        buttonShare = null;
        textRecordingStatus = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Releasing audio resources and shutting down executor.");
        // Release resources that are not tied to the view specifically
        releaseAudioRecord();
        releaseMediaPlayer();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            Log.d(TAG, "Executor service shutdown requested.");
        }
    }


    // --- WAV File Helper Methods --- (Ignorable Warnings Here)

    /**
     * Writes a basic WAV file header.
     * @param out The FileOutputStream to write to.
     * @param totalAudioLenBytes The length of the audio data section (bytes). Set to 0 for placeholder.
     * @throws IOException if an I/O error occurs during writing.
     */
    private void writeWavHeader(OutputStream out, long totalAudioLenBytes) throws IOException {
        // Warnings about 'always 0' or 'always true' in this method can be ignored
        // as they relate to constants and the standard WAV format structure.
        long totalDataLen = totalAudioLenBytes + 36;
        long longSampleRate = SAMPLE_RATE;
        int channels = 1; // Simplified
        int bitsPerSample = 16; // Simplified
        long byteRate = longSampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign; header[33] = 0;
        header[34] = (byte) bitsPerSample; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLenBytes & 0xff);
        header[41] = (byte) ((totalAudioLenBytes >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLenBytes >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLenBytes >> 24) & 0xff);
        out.write(header, 0, 44);
    }

    /**
     * Updates the WAV header with the correct file sizes after recording is complete.
     * @param file The WAV file to update.
     * @param totalAudioLenBytes The final length of the audio data section (bytes).
     * @throws IOException if an I/O error occurs while accessing or writing to the file.
     */
    private void updateWavHeader(File file, long totalAudioLenBytes) throws IOException {
        long totalDataLen = totalAudioLenBytes + 36;
        byte[] sizes = new byte[8];
        sizes[0] = (byte) (totalDataLen & 0xff);
        sizes[1] = (byte) ((totalDataLen >> 8) & 0xff);
        sizes[2] = (byte) ((totalDataLen >> 16) & 0xff);
        sizes[3] = (byte) ((totalDataLen >> 24) & 0xff);
        sizes[4] = (byte) (totalAudioLenBytes & 0xff);
        sizes[5] = (byte) ((totalAudioLenBytes >> 8) & 0xff);
        sizes[6] = (byte) ((totalAudioLenBytes >> 16) & 0xff);
        sizes[7] = (byte) ((totalAudioLenBytes >> 24) & 0xff);
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            raf.seek(4);
            raf.write(sizes, 0, 4);
            raf.seek(40);
            raf.write(sizes, 4, 4);
            Log.d(TAG, "WAV header updated successfully for " + file.getName());
        } finally {
            if (raf != null) {
                try { raf.close(); } catch (IOException e) { Log.e(TAG, "Error closing RandomAccessFile", e); }
            }
        }
    }
}