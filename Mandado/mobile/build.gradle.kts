plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.carlosmvp.mandado"
    compileSdk = 34
    defaultConfig { applicationId = "com.carlosmvp.mandado"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].java.srcDir("../shared/src")
}
dependencies {
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
