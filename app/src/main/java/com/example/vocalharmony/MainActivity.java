package com.example.vocalharmony;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
// Removed unused Toast import
// import android.widget.Toast;

import androidx.annotation.NonNull; // Keep NonNull
import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration; // Keep this import
import androidx.navigation.ui.NavigationUI;

import com.example.vocalharmony.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashSet; // Import HashSet
import java.util.Set; // Import Set


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private AppBarConfiguration appBarConfiguration; // Keep as member variable

    @Override
    protected void onCreate(Bundle savedInstanceState) { // Removed @NonNull here, not typical for onCreate
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = binding.navView; // Use binding

        // --- Define the set of top-level destination IDs ---
        // --- These MUST match the IDs in your UPDATED bottom_nav_menu.xml ---
        Set<Integer> topLevelDestinations = new HashSet<>();
        topLevelDestinations.add(R.id.navigation_training);
        topLevelDestinations.add(R.id.navigation_microphone_test);
        topLevelDestinations.add(R.id.navigation_voice_quality);
        topLevelDestinations.add(R.id.navigation_record_yourself);
        topLevelDestinations.add(R.id.navigation_data);
        // Add any other fragments directly accessible from the bottom bar

        // --- Build AppBarConfiguration with the dynamic set ---
        appBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations).build();

        // --- Setup NavController and link UI components ---
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration); // Setup ActionBar (optional but good)
        NavigationUI.setupWithNavController(navView, navController); // Setup BottomNavigationView

        Log.d(TAG, "MainActivity created. Navigation setup complete.");
    }

    // Removed permission checking methods (should be handled in Fragments)

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        // Allow NavigationUI to handle ActionBar item clicks corresponding to Nav destinations
        return NavigationUI.onNavDestinationSelected(item, navController)
                || super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Handles the Up button in the ActionBar
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        // Uses the member variable appBarConfiguration correctly
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}