plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "it.studio.offline"
 compileSdk = 35
 defaultConfig { applicationId = "it.studio.offline"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2" }
 signingConfigs { getByName("debug") { storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "studiooffline"; keyPassword = "android" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("com.google.mediapipe:tasks-genai:0.10.27"); implementation("com.tom-roush:pdfbox-android:2.0.27.0") }
