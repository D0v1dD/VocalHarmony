package com.example.vocalharmony.ui.dashboard; // Or your workers package

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// --- Firebase & Tasks Imports ---
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException; // Import needed
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
// --- End Firebase Imports ---

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class UploadWorker extends Worker {

    private static final String TAG = "UploadWorker";
    private static final long UPLOAD_TIMEOUT_MINUTES = 5; // Set a timeout for the upload attempt
    private static final long METADATA_UPLOAD_TIMEOUT_MINUTES = 1; // Shorter timeout for metadata

    // Keys to retrieve input data passed from the Fragment
    public static final String KEY_FILE_URI = "FILE_URI";
    public static final String KEY_USER_ID = "USER_ID";
    public static final String KEY_SENTENCE = "SENTENCE";
    public static final String KEY_TIMESTAMP = "TIMESTAMP"; // Recording creation timestamp
    public static final String KEY_TARGET_FILENAME = "TARGET_FILENAME"; // Filename (e.g., Rec_hash_time.m4a)

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Retrieve input data
        Data inputData = getInputData();
        String fileUriString = inputData.getString(KEY_FILE_URI);
        String userId = inputData.getString(KEY_USER_ID);
        String sentence = inputData.getString(KEY_SENTENCE);
        long timestamp = inputData.getLong(KEY_TIMESTAMP, 0);
        String targetAudioFilename = inputData.getString(KEY_TARGET_FILENAME); // Use this variable name consistently

        // Validate input data
        if (fileUriString == null || userId == null || sentence == null || targetAudioFilename == null) {
            Log.e(TAG, "Missing input data for UploadWorker.");
            return Result.failure();
        }

        Log.i(TAG, "doWork: Starting upload process for user: " + userId + ", file: " + targetAudioFilename);
        Log.d(TAG, "doWork: Sentence: " + sentence);

        try {
            Uri fileUri = Uri.parse(fileUriString);
            FirebaseStorage storage = FirebaseStorage.getInstance();

            // --- Upload Audio File ---
            String audioCloudPath = "recordings/" + userId + "/" + targetAudioFilename;
            StorageReference audioRef = storage.getReference().child(audioCloudPath);
            Log.d(TAG, "Audio Cloud Storage Path: " + audioRef.getPath());

            UploadTask audioUploadTask = audioRef.putFile(fileUri);
            Log.d(TAG, "Waiting for AUDIO upload task completion (Timeout: " + UPLOAD_TIMEOUT_MINUTES + " min)...");
            Task<UploadTask.TaskSnapshot> audioAwaitableTask = audioUploadTask;
            UploadTask.TaskSnapshot audioSnapshot = Tasks.await(audioAwaitableTask, UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (!audioAwaitableTask.isSuccessful()) {
                Log.e(TAG, "AUDIO upload task failed for " + targetAudioFilename + " after await.", audioAwaitableTask.getException());
                return Result.failure(createErrorData(audioAwaitableTask.getException()));
            }

            long bytesTransferred = audioSnapshot.getBytesTransferred();
            long totalBytes = audioSnapshot.getTotalByteCount();
            Log.i(TAG, "AUDIO upload successful: " + bytesTransferred + "/" + totalBytes + " bytes for " + targetAudioFilename);

            // --- Create and Upload Metadata JSON ---
            Log.d(TAG, "Preparing metadata upload...");
            String targetMetadataFilename = targetAudioFilename + ".json";
            String metadataCloudPath = "recordings/" + userId + "/" + targetMetadataFilename;
            StorageReference metadataRef = storage.getReference().child(metadataCloudPath);
            Log.d(TAG, "Metadata Cloud Storage Path: " + metadataRef.getPath());

            JSONObject metadataJson = new JSONObject();
            try {
                metadataJson.put("userId", userId);
                metadataJson.put("sentenceText", sentence);
                metadataJson.put("recordingTimestamp", timestamp);
                metadataJson.put("uploadTimestamp", System.currentTimeMillis());
                metadataJson.put("audioFilename", targetAudioFilename);
                metadataJson.put("audioStoragePath", audioRef.getPath());
            } catch (JSONException jsonE) {
                Log.e(TAG, "Failed to create metadata JSON object", jsonE);
                return Result.failure(createErrorData(jsonE));
            }

            byte[] metadataBytes = metadataJson.toString(2).getBytes(); // Pretty print JSON
            UploadTask metadataUploadTask = metadataRef.putBytes(metadataBytes);
            Log.d(TAG, "Waiting for METADATA upload task completion (Timeout: " + METADATA_UPLOAD_TIMEOUT_MINUTES + " min)...");
            Task<UploadTask.TaskSnapshot> metadataAwaitableTask = metadataUploadTask;
            Tasks.await(metadataAwaitableTask, METADATA_UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (metadataAwaitableTask.isSuccessful()) {
                Log.i(TAG, "Metadata upload successful for " + targetMetadataFilename);
                return Result.success(); // BOTH uploads successful
            } else {
                Log.e(TAG, "METADATA upload task failed for " + targetMetadataFilename + " after await.", metadataAwaitableTask.getException());
                return Result.failure(createErrorData(metadataAwaitableTask.getException()));
            }

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // *** Use targetAudioFilename in log ***
            Log.e(TAG, "ExecutionException during upload for " + targetAudioFilename, cause != null ? cause : e);
            if (cause instanceof StorageException) {
                StorageException storageEx = (StorageException) cause; int errorCode = storageEx.getErrorCode();
                Log.w(TAG, "StorageException Code: " + errorCode + ", Message: " + storageEx.getMessage());
                if (errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED || errorCode == StorageException.ERROR_UNKNOWN) {
                    Log.w(TAG, "Retryable StorageException (" + errorCode + "), returning Result.retry()"); return Result.retry();
                } else { Log.e(TAG, "Non-retryable StorageException (" + errorCode + "), returning Result.failure()"); return Result.failure(createErrorData(storageEx)); }
            } else { Log.e(TAG, "Non-Storage ExecutionException, returning Result.failure()"); return Result.failure(createErrorData(cause != null ? cause : e)); }
        } catch (TimeoutException e) {
            // *** Use targetAudioFilename in log ***
            Log.e(TAG, "Upload timed out after specified duration for " + targetAudioFilename, e); return Result.retry();
        } catch (InterruptedException e) {
            // *** Use targetAudioFilename in log ***
            Log.e(TAG, "Upload interrupted for " + targetAudioFilename, e); Thread.currentThread().interrupt(); return Result.failure(createErrorData(e));
        } catch (Exception e) {
            // *** Use targetAudioFilename in log ***
            Log.e(TAG, "Unexpected error during upload process for " + targetAudioFilename, e); return Result.failure(createErrorData(e));
        }
    }

    /** Helper to create Data object containing error message (optional) */
    private Data createErrorData(Throwable throwable) {
        return new Data.Builder()
                .putString("WORKER_ERROR_MSG", throwable != null ? throwable.getMessage() : "Unknown worker error")
                .build();
    }
}