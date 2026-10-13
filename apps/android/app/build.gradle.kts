plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val mobileAbis = providers.gradleProperty("mobileAbis").orElse("arm64-v8a,x86_64").get().split(",")
android {
    namespace = "com.lightcraft.mobile"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "com.lightcraft.mobile"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += mobileAbis }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
// Fail the build before packaging an unusable app with a missing native engine.
tasks.register("verifyNativeEngine") {
    doLast {
        mobileAbis.forEach { abi ->
            check(file("src/main/jniLibs/$abi/liblightcraft_mobile.so").isFile) {
                "Native engine missing for $abi. Run scripts/build-android.ps1 -Abi all or scripts/build-android.sh first."
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn("verifyNativeEngine") }
