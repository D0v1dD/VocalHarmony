package com.example.vocalharmony;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
// Removed unused Toast import

import androidx.annotation.NonNull; // Keep NonNull if needed by super methods, otherwise remove
import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.vocalharmony.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashSet;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    // appBarConfiguration might not be strictly needed anymore if not setting up ActionBar
    // private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = binding.navView;

        // Define the set of top-level destination IDs - Still needed for correct
        // highlighting and behavior of the BottomNavigationView even without an ActionBar.
        Set<Integer> topLevelDestinations = new HashSet<>();
        topLevelDestinations.add(R.id.navigation_training);
        topLevelDestinations.add(R.id.navigation_microphone_test);
        topLevelDestinations.add(R.id.navigation_voice_quality);
        topLevelDestinations.add(R.id.navigation_record_yourself);
        topLevelDestinations.add(R.id.navigation_data);
        // Add any other fragments directly accessible from the bottom bar

        // --- We might still need AppBarConfiguration for setupWithNavController if it influences highlighting,
        // --- but we won't use it with the (non-existent) ActionBar. Let's keep it for now.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations).build();

        // --- Setup NavController and link BottomNavigationView ---
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        // *** REMOVED/COMMENTED OUT the line causing the crash ***
        // NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // *** KEEP this line to make the BottomNavigationView work ***
        NavigationUI.setupWithNavController(navView, navController);

        Log.d(TAG, "MainActivity created. Navigation setup complete.");
    }


    // *** COMMENTED OUT as it's related to ActionBar menu items ***
    /*
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        // Allow NavigationUI to handle ActionBar item clicks corresponding to Nav destinations
        return NavigationUI.onNavDestinationSelected(item, navController)
                || super.onOptionsItemSelected(item);
    }
    */

    // *** COMMENTED OUT as it's related to the ActionBar's Up button ***
    /*
    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        // NavigationUI.navigateUp needs the AppBarConfiguration, but won't be called without an ActionBar
        return NavigationUI.navigateUp(navController, appBarConfiguration) // Use local var if member removed
                || super.onSupportNavigateUp();
    }
    */
}