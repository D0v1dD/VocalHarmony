package com.example.vocalharmony.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;

public class AccessDataFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_access_data, container, false);

        // Set up the title and the scores
        TextView textViewLatestScore = root.findViewById(R.id.text_latest_score);
        TextView textViewS2T = root.findViewById(R.id.text_s2t);
        TextView textViewS2N = root.findViewById(R.id.text_s2n);
        TextView textViewBestScore = root.findViewById(R.id.text_best_score);
        TextView textViewBestS2T = root.findViewById(R.id.text_best_s2t);
        TextView textViewBestS2N = root.findViewById(R.id.text_best_s2n);

        textViewLatestScore.setText("Latest Score");
        textViewS2T.setText("S2T: 0"); // Replace 0 with actual score if available
        textViewS2N.setText("S2N: 0"); // Replace 0 with actual score if available

        textViewBestScore.setText("Best Score");
        textViewBestS2T.setText("S2T: 0"); // Replace 0 with actual best score if available
        textViewBestS2N.setText("S2N: 0"); // Replace 0 with actual best score if available

        return root;
    }
}
