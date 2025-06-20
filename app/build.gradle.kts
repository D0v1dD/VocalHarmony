plugins {
    // Your existing line (using alias syntax from version catalog likely)
    alias(libs.plugins.android.application)

    // *** Add this line for the Google Services plugin ***
    alias(libs.plugins.googleServices) // Use the alias defined in libs.versions.toml
}

android {
    // namespace = "com.example.vocalharmony" // This should still be here
    namespace = "com.example.vocalharmony" // Keeping your original namespace
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vocalharmony.devversionrecord" // Your new ID
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // You only need one buildFeatures block
    buildFeatures {
        viewBinding = true
    }
}

// Remove the duplicate android { buildFeatures { ... } } block

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0") // Consider updating to 1.10.0+ later
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment:2.5.3") // Consider updating nav components later
    implementation("androidx.navigation:navigation-ui:2.5.3")

    // MPAndroidChart & JTransforms
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.github.wendykierp:JTransforms:3.1")

    // Core and RecyclerView (already added)
    implementation("androidx.core:core:1.9.0") // Or core-ktx if using Kotlin
    implementation("androidx.recyclerview:recyclerview:1.3.1")

    // --- Firebase & WorkManager Dependencies ---

    // *** Add Firebase Bill of Materials (BoM) ***
    // Make sure to check Firebase docs for the latest BoM version compatible with your project
    implementation(platform("com.google.firebase:firebase-bom:33.13.0")) // Example version

    // *** Add specific Firebase SDKs (versions managed by BoM) ***
    implementation("com.google.firebase:firebase-auth")      // For Authentication
    implementation("com.google.firebase:firebase-storage")  // For Cloud Storage
    implementation("com.google.firebase:firebase-analytics")
    // *** Add WorkManager ***
    // Check AndroidX docs for latest WorkManager version
    implementation("androidx.work:work-runtime:2.9.0") // Example version

    // --- Testing Dependencies ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}