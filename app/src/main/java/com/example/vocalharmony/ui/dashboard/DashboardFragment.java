package com.example.vocalharmony.ui.dashboard;

import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;
import com.example.vocalharmony.databinding.FragmentDashboardBinding;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // ViewModel is no longer needed here as the text is static.

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // The text is now set directly in the layout xml file.
        // We can, however, process the HTML tags from the string resource here.
        final TextView textView = binding.textDashboard;

        String consentHtml = getString(R.string.tutorial_dashboard_description);
        Spanned formattedText;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            formattedText = Html.fromHtml(consentHtml, Html.FROM_HTML_MODE_LEGACY);
        } else {
            //noinspection deprecation
            formattedText = Html.fromHtml(consentHtml);
        }
        textView.setText(formattedText);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}