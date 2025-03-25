plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.driveease"
    compileSdk = 34  // ✅ Kept compileSdk at 34 for stability

    viewBinding {
        enable = true  // ✅ ViewBinding enabled for XML UI components
    }

    buildFeatures {
        compose = true  // ✅ Jetpack Compose enabled
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    defaultConfig {
        applicationId = "com.example.driveease"
        minSdk = 24
        targetSdk = 34  // ✅ Kept targetSdk at 34 to match compileSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-auth:20.6.0")

    // ✅ Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.3.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // ✅ Dependencies for splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ✅ Dependencies for Google Maps
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.libraries.places:places:4.1.0")

    // ✅ Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")

    // ✅ Jetpack Compose dependencies (All versions aligned to 1.5.4)
    implementation("androidx.activity:activity-compose:1.8.2") // 🔽 Downgraded from 1.10.0
    implementation("androidx.compose.ui:ui:1.5.4") // 🔽 Downgraded from 1.7.7
    implementation("androidx.compose.material:material:1.5.4") // 🔽 Downgraded from 1.7.7
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4") // 🔽 Downgraded from 1.7.7
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2") // 🔽 Downgraded from 2.8.7
    implementation("androidx.navigation:navigation-compose:2.6.0") // 🔽 Downgraded from 2.8.6
    implementation("androidx.compose.runtime:runtime-livedata:1.5.4") // 🔽 Downgraded from 1.7.7
    implementation("androidx.compose.material3:material3:1.1.2")

    implementation("androidx.compose.foundation:foundation:1.6.0")

    // animation Dependencies
    implementation("com.google.accompanist:accompanist-navigation-animation:0.31.5-beta")
    implementation("com.google.maps.android:maps-compose:2.11.4")

    // // OSMDroid core
    implementation("org.osmdroid:osmdroid-android:6.1.16")

    // MapsCompose for Jetpack Compose
    implementation("org.osmdroid:osmdroid-wms:6.1.16") // ✅ If you need WMS support


    // Animation dependencies
    implementation("androidx.compose.animation:animation:1.5.0")
    implementation("androidx.compose.material:material-icons-extended:1.5.0")

    // retrofit dependecies
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // new addition
    debugImplementation ("androidx.compose.ui:ui-tooling:1.5.4")




    // ✅ Testing Dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")

    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")
    }
}


