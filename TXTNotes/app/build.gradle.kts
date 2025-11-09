plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "barilyuk.texetescribe"
    compileSdk = 34

    defaultConfig {
        applicationId = "barilyuk.texetescribe"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    implementation(libs.documentfile)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Add juniversalchardet FOSS library for encoding detection
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")
}