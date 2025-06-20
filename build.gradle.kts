// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Declare Android Application plugin (apply in app module)
    alias(libs.plugins.android.application) apply false

    // *** Add this line to declare the Google Services plugin ***
    // Use the same version (e.g., 4.4.2) we discussed previously
    id("com.google.gms.google-services") version "4.4.2" apply false
}