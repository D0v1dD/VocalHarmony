package com.example.vocalharmony.ui.notifications;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.vocalharmony.R;
import com.example.vocalharmony.databinding.FragmentNotificationsBinding;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class NotificationsFragment extends Fragment {

    private static final String TAG = "NotificationsFragment";
    private static final String UNIQUE_WORK_NAME = "VocalHarmonyDailyReminder";

    private FragmentNotificationsBinding binding;
    private WorkManager workManager;

    private TextView statusTextView;
    private Button scheduleButton;
    private Button cancelButton;

    // Launcher for Notification Permission
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(getContext(), "Notification permission granted.", Toast.LENGTH_SHORT).show();
                    scheduleReminder(); // Try scheduling again after getting permission
                } else {
                    Toast.makeText(getContext(), "Notification permission denied. Reminders cannot be shown.", Toast.LENGTH_LONG).show();
                }
            });

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize WorkManager
        workManager = WorkManager.getInstance(requireContext());

        // Find views
        statusTextView = binding.textNotificationStatus;
        scheduleButton = binding.buttonSchedule;
        cancelButton = binding.buttonCancel;

        // Set up button listeners
        scheduleButton.setOnClickListener(v -> scheduleReminder());
        cancelButton.setOnClickListener(v -> cancelReminder());

        // Observe the work status to update the UI
        observeWorkStatus();

        return root;
    }

    private void scheduleReminder() {
        // First, check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                Log.d(TAG, "Notification permission not granted, requesting...");
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return; // Exit the method, will be called again if permission is granted
            }
        }

        // If permission is granted (or not needed on older OS), schedule the work
        Log.d(TAG, "Scheduling daily reminder work.");

        // Create a periodic request to run once a day
        PeriodicWorkRequest reminderRequest =
                new PeriodicWorkRequest.Builder(ReminderWorker.class, 1, TimeUnit.DAYS)
                        // You can set constraints, like requiring network or battery
                        // .setConstraints(Constraints.NONE)
                        .build();

        // Enqueue the work as unique, so we don't schedule duplicates
        workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep the existing work if one is already scheduled
                reminderRequest);

        Toast.makeText(getContext(), "Daily reminders turned ON.", Toast.LENGTH_SHORT).show();
    }

    private void cancelReminder() {
        Log.d(TAG, "Cancelling daily reminder work.");
        // Cancel the unique work by its name
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME);
        Toast.makeText(getContext(), "Daily reminders turned OFF.", Toast.LENGTH_SHORT).show();
    }

    private void observeWorkStatus() {
        // Observe the unique work to update the status TextView
        workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME).observe(getViewLifecycleOwner(), workInfos -> {
            if (workInfos == null || workInfos.isEmpty()) {
                statusTextView.setText(R.string.notifications_status_inactive);
                return;
            }

            // Check the status of the first (and only) work info
            WorkInfo workInfo = workInfos.get(0);
            if (workInfo.getState() == WorkInfo.State.CANCELLED || workInfo.getState() == WorkInfo.State.FAILED) {
                statusTextView.setText(R.string.notifications_status_inactive);
            } else {
                statusTextView.setText(R.string.notifications_status_active);
            }
        });
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}