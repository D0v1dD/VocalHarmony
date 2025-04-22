package com.example.vocalharmony.ui.home; // Adjust package if needed

import java.util.Objects;

/**
 * Simple data class to hold information about a saved recording.
 * Now includes setters/update method to support renaming.
 */
public class RecordingItem {

    // --- Fields ---
    private String fileName; // Made non-final
    private String filePath; // Made non-final
    private final long timestamp; // Milliseconds since epoch (remains final)
    private String formattedDate; // Store pre-formatted date/time string
    private String durationString; // Store pre-formatted duration string

    // --- Constructor ---
    public RecordingItem(String fileName, String filePath, long timestamp) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.timestamp = timestamp;
        // Initialize placeholders - they should be set later during loading or update
        this.formattedDate = "";
        this.durationString = "";
    }

    // --- Getters ---
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public long getTimestamp() { return timestamp; }
    public String getFormattedDate() { return formattedDate; }
    public String getDurationString() { return durationString; }

    // --- Setters (or an update method) ---
    // Individual setters if preferred
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    // Method to update both after a successful rename
    public void updateNameAndPath(String newFileName, String newFilePath) {
        this.fileName = newFileName;
        this.filePath = newFilePath;
        // Note: Formatted date usually depends on timestamp, not filename,
        // so it might not need updating unless your formatting includes the filename.
        // Duration also doesn't change with rename.
    }

    // Setters for formatted strings (used during loading)
    public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }
    public void setDurationString(String durationString) { this.durationString = durationString; }


    // --- equals() and hashCode() ---
    // Based on filePath as a unique identifier for a recording file.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordingItem that = (RecordingItem) o;
        // If filePath is the primary unique ID, use it. Otherwise, adjust.
        return Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    // --- toString() (Optional: Useful for debugging) ---
    @Override
    public String toString() {
        return "RecordingItem{" +
                "fileName='" + fileName + '\'' +
                ", filePath='" + filePath + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}