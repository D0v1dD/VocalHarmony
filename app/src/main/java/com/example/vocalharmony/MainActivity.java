package com.example.vocalharmony; // Ensure this matches your package

import android.os.Bundle;
import android.util.Log;
// import android.view.MenuItem; // Not needed unless ActionBar methods are uncommented
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
// REMOVED ViewBinding Import
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashSet;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String AUTH_TAG = "MainActivityAuth";

    // REMOVED binding variable

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use traditional setContentView
        setContentView(R.layout.activity_main); // Assumes layout is activity_main.xml

        // --- Find views using findViewById ---
        // *** CORRECTED the ID here to match activity_main.xml ***
        BottomNavigationView navView = findViewById(R.id.nav_view); // Use nav_view (snake_case)

        // Add null check for safety
        if (navView == null) {
            Log.e(TAG, "FATAL ERROR: BottomNavigationView with ID 'nav_view' not found in layout R.layout.activity_main");
            Toast.makeText(this, "Layout Error: Cannot find Navigation View", Toast.LENGTH_LONG).show();
            return; // Prevent crash if view not found
        }


        // --- NavController Setup ---
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

        // --- ActionBar Setup (Commented out as before) ---
        // NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // --- Standard BottomNavigationView Handling ---
        NavigationUI.setupWithNavController(navView, navController); // This should now work

        Log.d(TAG, "MainActivity created. Using findViewById. Navigation setup complete.");


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