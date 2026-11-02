plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.storycut.app"; compileSdk = 35
    defaultConfig { applicationId = "com.storycut.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
}
