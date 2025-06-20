package com.example.vocalharmony; // Ensure this matches your package

// --- Added Imports for Notification Channel ---
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
// --- End Added Imports ---

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

// Firebase Auth Imports
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Import R file
import com.example.vocalharmony.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String AUTH_TAG = "MainActivityAuth";

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        BottomNavigationView navView = findViewById(R.id.nav_view);

        if (navView == null) {
            Log.e(TAG, "FATAL ERROR: BottomNavigationView with ID 'nav_view' not found in layout R.layout.activity_main");
            Toast.makeText(this, "Layout Error: Cannot find Navigation View", Toast.LENGTH_LONG).show();
            return;
        }

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        // Define top-level destinations
        Set<Integer> topLevelDestinations = new HashSet<>();
        topLevelDestinations.add(R.id.navigation_training); // Use your actual IDs
        topLevelDestinations.add(R.id.navigation_microphone_test);
        topLevelDestinations.add(R.id.navigation_voice_quality);
        topLevelDestinations.add(R.id.navigation_record_yourself);
        topLevelDestinations.add(R.id.navigation_data);
        // Add other top-level IDs

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations).build();

        // --- Standard BottomNavigationView Handling ---
        NavigationUI.setupWithNavController(navView, navController);

        Log.d(TAG, "MainActivity created. Navigation setup complete.");

        // --- ADDED: Create Notification Channel on startup ---
        createNotificationChannel();


        // --- Firebase Anonymous Authentication Logic (Keep as before) ---
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(AUTH_TAG, "No user signed in. Attempting anonymous sign-in...");
            signInAnonymously();
        } else {
            Log.d(AUTH_TAG, "User already signed in anonymously. UID: " + currentUser.getUid());
        }
        // --- End Firebase Auth ---
    }

    /**
     * ADDED: Creates the Notification Channel for daily reminders.
     * Required for Android 8.0 (API 26) and above.
     */
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel_name);
            String description = getString(R.string.notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            // The ID "VocalHarmonyReminderChannel" must match the ID used in ReminderWorker
            NotificationChannel channel = new NotificationChannel("VocalHarmonyReminderChannel", name, importance);
            channel.setDescription(description);

            // Register the channel with the system. You can't change the importance
            // or other notification behaviors after this.
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created.");
            }
        }
    }


    /**
     * Anonymous Sign-in Method (Keep as before)
     */
    private void signInAnonymously() {
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(AUTH_TAG, "signInAnonymously: SUCCESS");
                            FirebaseUser user = mAuth.getCurrentUser();
                            Log.d(AUTH_TAG, "Anonymous User UID: " + (user != null ? user.getUid() : "null user object"));
                        } else {
                            Log.w(AUTH_TAG, "signInAnonymously: FAILURE", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed. Data collection might not work.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // --- Commented out ActionBar methods (Keep as before) ---
    /*
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) { ... }
    */
    /*
    @Override
    public boolean onSupportNavigateUp() { ... }
    */

}