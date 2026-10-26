plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "it.studio.offline"
 compileSdk = 35
 defaultConfig { applicationId = "it.studio.offline"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("com.google.mediapipe:tasks-genai:0.10.27") }
