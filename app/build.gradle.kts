plugins {
    alias(libs.plugins.android.application)
}


android {
    namespace = "com.example.vocalharmony"
    compileSdk = 34


    defaultConfig {
        applicationId = "com.example.vocalharmony.devversion"
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


    buildFeatures {
        viewBinding = true
    }
}

android {
    buildFeatures {
        viewBinding = true
    }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment:2.5.3")
    implementation("androidx.navigation:navigation-ui:2.5.3")

    // MPAndroidChart for charting functionality
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // JTransforms for FFT
    implementation("com.github.wendykierp:JTransforms:3.1")

    // *** ADD THESE LINES ***
    implementation("androidx.core:core:1.9.0") // Provides ContextCompat, FileProvider etc. (Use 1.9.0 or later)
    // Or use implementation("androidx.core:core-ktx:1.9.0") if you primarily use Kotlin elsewhere
    implementation("androidx.recyclerview:recyclerview:1.3.1") // Provides RecyclerView components (Use 1.3.0 or later)
    // *** END OF ADDED LINES ***

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}



