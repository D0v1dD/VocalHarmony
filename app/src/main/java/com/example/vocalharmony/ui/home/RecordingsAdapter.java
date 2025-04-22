package com.example.vocalharmony.ui.home; // Adjust package if needed

import android.content.Context;
import android.util.Log; // For potential logging
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil; // Import for potential future optimization
import androidx.recyclerview.widget.ListAdapter; // Consider extending ListAdapter for DiffUtil
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocalharmony.R; // Ensure R is imported correctly

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying a list of RecordingItem objects in a RecyclerView.
 * Includes click and long-click listeners for actions like play, share, delete, and rename.
 */
// Note: Consider extending ListAdapter<RecordingItem, RecordingsAdapter.RecordingViewHolder>
// in the future for automatic DiffUtil implementation if performance becomes an issue.
// For now, extending RecyclerView.Adapter is fine.
public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.RecordingViewHolder> {

    private static final String TAG = "RecordingsAdapter"; // For logging

    // Use List directly, copying in submitList ensures adapter has its own instance.
    private List<RecordingItem> recordingsList = new ArrayList<>();
    private final LayoutInflater inflater;
    private final OnItemActionListener listener;

    /**
     * Interface definition for callbacks to be invoked when actions are performed
     * on an item in the RecyclerView.
     */
    public interface OnItemActionListener {
        void onPlayClick(RecordingItem item, int position);
        void onShareClick(RecordingItem item, int position);
        void onDeleteClick(RecordingItem item, int position);
        // Added for Rename functionality
        void onItemLongClick(RecordingItem item, int position);
    }

    /**
     * Constructor for the RecordingsAdapter.
     * @param context The context used to inflate layouts.
     * @param listener The listener that will handle item actions.
     */
    public RecordingsAdapter(@NonNull Context context, @NonNull OnItemActionListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    /**
     * Updates the list of recordings displayed by the adapter.
     * @param newList The new list of RecordingItems to display.
     */
    public void submitList(@NonNull List<RecordingItem> newList) {
        // Create a new list to prevent external modifications affecting the adapter's list
        this.recordingsList = new ArrayList<>(newList);
        // TODO: Replace notifyDataSetChanged with DiffUtil for better performance,
        // especially with frequent list updates or large lists.
        // Using ListAdapter automatically handles DiffUtil.
        notifyDataSetChanged(); // Simple update - Triggers Lint warning about performance
        Log.d(TAG, "Submitted new list with size: " + recordingsList.size());
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = inflater.inflate(R.layout.list_item_recording, parent, false);
        return new RecordingViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingViewHolder holder, int position) {
        // Check bounds to avoid potential IndexOutOfBoundsException
        if (position >= 0 && position < recordingsList.size()) {
            RecordingItem currentItem = recordingsList.get(position);
            holder.bind(currentItem, listener);
        } else {
            Log.e(TAG, "Invalid position in onBindViewHolder: " + position + ", list size: " + recordingsList.size());
            // Optionally hide the holder's view or display an error state
        }
    }

    @Override
    public int getItemCount() {
        return recordingsList.size();
    }

    // --- ViewHolder Class ---
    // Must be public static if it's an inner class accessed from outside (like ListAdapter might require)
    // Or just public if RecordingsAdapter itself is not static inner class.
    public static class RecordingViewHolder extends RecyclerView.ViewHolder {
        private final TextView textRecordingName;
        private final ImageButton buttonPlayItem;
        private final ImageButton buttonShareItem;
        private final ImageButton buttonDeleteItem;

        public RecordingViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ensure these IDs exist in list_item_recording.xml
            textRecordingName = itemView.findViewById(R.id.text_recording_name);
            buttonPlayItem = itemView.findViewById(R.id.button_play_item);
            buttonShareItem = itemView.findViewById(R.id.button_share_item);
            buttonDeleteItem = itemView.findViewById(R.id.button_delete_item);
        }

        /**
         * Binds a RecordingItem's data to the ViewHolder's views and sets listeners.
         * @param item The RecordingItem data for this position.
         * @param listener The listener to handle actions.
         */
        public void bind(final RecordingItem item, final OnItemActionListener listener) {
            if (item == null) {
                Log.e(TAG, "bind called with null item at position " + getBindingAdapterPosition());
                // Optionally clear views or show an error state
                textRecordingName.setText(R.string.error_loading_item); // Example error text
                return;
            }

            // Display filename and duration (assuming they are set in RecordingItem)
            String displayInfo = item.getFileName(); // Start with filename
            if (item.getDurationString() != null && !item.getDurationString().isEmpty() && !item.getDurationString().equals("?:??")) {
                displayInfo += " (" + item.getDurationString() + ")"; // Append duration if valid
            }
            textRecordingName.setText(displayInfo);
            // TODO: Add content descriptions that include the filename for accessibility

            // --- Set Click Listeners ---
            // Use getBindingAdapterPosition() as it's generally safer during potential list changes.
            buttonPlayItem.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) { listener.onPlayClick(item, pos); }
                }
            });

            buttonShareItem.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) { listener.onShareClick(item, pos); }
                }
            });

            buttonDeleteItem.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) { listener.onDeleteClick(item, pos); }
                }
            });

            // --- Set Long Click Listener for Rename ---
            // Attach to the root itemView of the ViewHolder
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    int pos = getBindingAdapterPosition(); // Get current position
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onItemLongClick(item, pos); // Call the interface method
                        return true; // Return true to indicate the long click was consumed
                    }
                }
                return false; // Return false if no listener or invalid position
            });
        }
    }
}