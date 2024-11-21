package com.example.vocalharmony.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.vocalharmony.R;
import com.example.vocalharmony.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        Button buttonTraining = root.findViewById(R.id.button_training);
        buttonTraining.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.navigation_training);
        });

        Button buttonHowDoISound = root.findViewById(R.id.button_how_do_i_sound);
        buttonHowDoISound.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.navigation_record_yourself);
        });

        Button buttonSavedFiles = root.findViewById(R.id.button_saved_files);
        buttonSavedFiles.setOnClickListener(v -> {
            // Handle the action for Saved Files button here
        });

        Button buttonAccessData = root.findViewById(R.id.button_access_data);
        buttonAccessData.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.navigation_data);
        });



        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}