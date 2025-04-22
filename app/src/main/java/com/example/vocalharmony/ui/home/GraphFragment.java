package com.example.vocalharmony.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources; // Keep if needed elsewhere, maybe by Log.e call? Check imports after changes.
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
// Removed TypedValue import
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

// Removed AttrRes, ColorInt imports
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.vocalharmony.R;

// MPAndroidChart Imports
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;


public class GraphFragment extends Fragment {

    private static final String TAG = "GraphFragment";
    private static final String PREFS_NAME = "VocalHarmonyPrefs";
    private static final String SNR_KEY_PREFIX = "snr_";
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    private LineChart historyLineChart;
    private TextView textNoData;
    private SimpleDateFormat keyDateFormat;
    private SimpleDateFormat labelDateFormat;

    private static class SnrDataPoint {
        final long timestampMillis;
        final float snrValue;
        final Date date;

        SnrDataPoint(long timestampMillis, float snrValue, Date date) {
            this.timestampMillis = timestampMillis;
            this.snrValue = snrValue;
            this.date = date;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyDateFormat = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
        keyDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        labelDateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        historyLineChart = view.findViewById(R.id.history_line_chart);
        textNoData = view.findViewById(R.id.text_no_data);
        if (historyLineChart == null) Log.e(TAG, "Error: History Line Chart not found!");
        if (textNoData == null) Log.e(TAG, "Error: No Data TextView not found!");
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        if (historyLineChart != null) {
            setupHistoryChart();
            loadAndDisplaySnrData();
        } else {
            if (textNoData != null) {
                textNoData.setText(getString(R.string.graph_init_error));
                textNoData.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupHistoryChart() {
        Log.d(TAG, "Setting up History Line Chart...");
        // Styling unchanged...
        historyLineChart.setDrawGridBackground(false);
        historyLineChart.setDrawBorders(true);
        historyLineChart.setBorderColor(Color.LTGRAY);
        historyLineChart.setTouchEnabled(true);
        historyLineChart.setDragEnabled(true);
        historyLineChart.setScaleEnabled(true);
        historyLineChart.setPinchZoom(true);
        historyLineChart.getLegend().setEnabled(false);
        Description description = new Description();
        description.setEnabled(false);
        historyLineChart.setDescription(description);
        // Axis setup unchanged...
        XAxis xAxis = historyLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45);
        YAxis leftAxis = historyLineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(30f);
        historyLineChart.getAxisRight().setEnabled(false);
        // No data text unchanged...
        historyLineChart.setNoDataText(getString(R.string.graph_loading_data));
        historyLineChart.invalidate();
    }


    private void loadAndDisplaySnrData() {
        if (getContext() == null || historyLineChart == null) { Log.e(TAG, "Cannot load data: Context or Chart is null."); return; }
        Log.d(TAG, "Loading SNR data from SharedPreferences...");
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        ArrayList<SnrDataPoint> dataPoints = new ArrayList<>();

        // Parsing loop unchanged...
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith(SNR_KEY_PREFIX)) {
                try {
                    String timestampStr = key.substring(SNR_KEY_PREFIX.length());
                    Date date = keyDateFormat.parse(timestampStr);
                    if (date != null) {
                        long timestampMillis = date.getTime();
                        float snrValue = (Float) entry.getValue();
                        dataPoints.add(new SnrDataPoint(timestampMillis, snrValue, date));
                        Log.v(TAG, "Parsed entry: Time=" + timestampMillis + ", SNR=" + snrValue + ", Date="+date);
                    }
                } catch (ParseException | ClassCastException e) { // Combine catches
                    Log.w(TAG, "Failed to parse/cast entry for key: " + key, e);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing key: " + key, e);
                }
            }
        }

        Log.d(TAG, "Found " + dataPoints.size() + " historical SNR data points.");

        // Handling no data unchanged...
        if (dataPoints.isEmpty()) { /* ... */ return; }
        else { if(textNoData != null) textNoData.setVisibility(View.GONE); }

        // Sorting unchanged...
        dataPoints.sort(Comparator.comparingLong(dp -> dp.timestampMillis));

        // Entry creation unchanged...
        final ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < dataPoints.size(); i++) { entries.add(new Entry(i, dataPoints.get(i).snrValue)); }


        LineDataSet dataSet = new LineDataSet(entries, "Max SNR History");
        Context context = requireContext();

        // *** USING DIRECT COLOR RESOURCES ***
        dataSet.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(ContextCompat.getColor(context, R.color.colorSecondary));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.DKGRAY); // Or ContextCompat.getColor(context, R.color.black)
        dataSet.setDrawFilled(true);
        // Use one of your defined colors for fill
        dataSet.setFillColor(ContextCompat.getColor(context, R.color.colorPrimary)); // Example: using primary
        // dataSet.setFillColor(ContextCompat.getColor(context, R.color.teal_200)); // Alternative
        dataSet.setFillAlpha(50); // Adjust transparency

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);
        LineData lineData = new LineData(dataSets);


        // X-Axis formatter setup unchanged...
        XAxis xAxis = historyLineChart.getXAxis();
        final ArrayList<SnrDataPoint> finalDataPoints = dataPoints; // Final for use in inner class
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int index = (int) value;
                if (index >= 0 && index < finalDataPoints.size()) {
                    return labelDateFormat.format(finalDataPoints.get(index).date);
                }
                return "";
            }
        });
        xAxis.setLabelCount(Math.min(dataPoints.size(), 6), false);

        // Set data and refresh unchanged...
        historyLineChart.setData(lineData);
        historyLineChart.invalidate();
        Log.i(TAG,"Chart data loaded and displayed.");
    }

    // *** REMOVED getColorFromAttr METHOD ***

}