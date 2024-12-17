package com.example.vocalharmony;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.vocalharmony.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up bottom navigation view and link it with navController
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Define top-level destinations for AppBarConfiguration
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_notifications)
                .build();

        // Initialize NavController and link with action bar and bottom navigation
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Check and request audio recording permission
        checkAudioPermission();
    }

    /**
     * Checks if RECORD_AUDIO permission is granted.
     * If not granted, requests the permission.
     */
    private void checkAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Audio permission not granted, requesting permission...");
            requestRecordAudioPermission();
        } else {
            Log.d(TAG, "Audio permission already granted.");
            permissionToRecordAccepted = true;
        }
    }

    /**
     * Requests the RECORD_AUDIO permission.
     */
    private void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
    }

    /**
     * Handles the result of the permission request dialog.
     * Provides user feedback and logs the result.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (permissionToRecordAccepted) {
                Toast.makeText(this, "Recording permission granted. You can now record audio.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Audio recording permission granted by user.");
            } else {
                Toast.makeText(this, "Recording permission is required for this feature.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Audio recording permission denied by user.");
                // Optionally guide user to allow permission from settings if needed.
            }
        }
    }

    /**
     * Handle action bar item clicks.
     * Currently, no additional action is defined except calling the super.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Add code here if you have menu items that need handling.
        return super.onOptionsItemSelected(item);
    }

    /**
     * Handles the Up navigation (Back button in the action bar).
     */
    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        boolean navigatedUp = navController.navigateUp();
        if (!navigatedUp) {
            Log.w(TAG, "navigateUp() returned false; may not be able to navigate up from current destination.");
        }
        return navigatedUp || super.onSupportNavigateUp();
    }
}
