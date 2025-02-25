plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "io.pawsomepals.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.pawsomepals.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 13
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
            buildConfigField(
                "String",
                "DEFAULT_MAPS_API_KEY",
                "\"AIzaSyAj_zBLXw67-FvMTCRcwXCeukgtrCZbcyk\""
            )
            buildConfigField(
                "String",
                "DEFAULT_GOOGLE_SIGN_IN_KEY",
                "\"455388576182-nb7hkru3benl1qe38epcccjd0kurebtl.apps.googleusercontent.com\""
            )
            buildConfigField(
                "String",
                "DEFAULT_RECAPTCHA_KEY",
                "\"6Lc3MYIqAAAAAEgk69-5Kl5vA7Zz-rnO9SJJkUPC\""
            )
            buildConfigField("String", "DEFAULT_FACEBOOK_APP_ID", "\"527141699750137\"")
            buildConfigField(
                "String",
                "DEFAULT_FACEBOOK_TOKEN",
                "\"5f8a74941436f7da5230b70876731479\""
            )
            buildConfigField(
                "String",
                "DEFAULT_OPENAI_KEY",
                "\"sk-OBO10XT3CzWpM-ulVeVmWU-EMVnO5wx2jb1IcmZf2bT3BlbkFJA49zMWGgDY559Dd3mdPNBiM-47ilg5NvqnGJOpZQUA\""
            )
            buildConfigField("String", "DEFAULT_WEATHER_KEY", "\"aa553d06e53c43aea30171022240810\"")

            vectorDrawables {
                useSupportLibrary = true
            }
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
    }

    kapt {
        correctErrorTypes = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }

    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("META-INF/AL2.0")
            excludes.add("META-INF/LGPL2.1")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/io.netty.versions.properties")
            excludes.add("META-INF/maven/**")
            excludes.add("META-INF/native-image/**")
            excludes.add("META-INF/proguard/**")
            excludes.add("META-INF/versions/**")
            excludes.add("META-INF/web-fragment.xml")
            excludes.add("META-INF/services/com.fasterxml.**")
            excludes.add("META-INF/services/org.xmlpull.v1.**")
            excludes.add("*.readme")
            excludes.add("*.txt")
            excludes.add("*.xml")
            excludes.add("*.properties")
            excludes.add("*.bin")
            excludes.add("*.json")
            excludes.add("okhttp3/**")
            pickFirsts.add("META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.identity.jvm)
    implementation(libs.androidx.hilt.common)
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.window:window:1.2.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // FlowRow and other experimental layouts
    implementation("androidx.compose.foundation:foundation-layout:1.6.1")


    // Compose Dependencies
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Calendar
    implementation("com.kizitonwose.calendar:compose:2.5.0")

    // Core Android Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.v161)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.exifinterface)

    // Window Size Classes and Adaptive Layouts

    // Architecture Components
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose.android)

    // Maps and Places
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.3.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.0")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Coroutines Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")



    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.datastore:datastore-preferences-core:1.1.1")

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-common-ktx")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.firebase:geofire-android-common:3.2.0")


    // Google Services
    implementation(libs.play.services.auth.v2070)
    implementation(libs.play.services.location.v2101)
    implementation(libs.places.v330)
    implementation(libs.play.services.safetynet)
    implementation(libs.recaptcha)

    // Google Calendar API
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.api.client.android)
    implementation(libs.google.oauth.client.jetty)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.google.http.client.gson)
    implementation(libs.google.http.client.jackson2)

    // Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android.v164)
    implementation(libs.androidx.lifecycle.runtime.ktx.v287)

    // Image Loading
    implementation(libs.coil.compose)

    // Social Login
    implementation(libs.facebook.login)

    // Media
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Camera
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Background Processing
    implementation(libs.androidx.work.runtime.ktx)

    // OpenAI Integration
    implementation(libs.openai.client)
    implementation(libs.ktor.client.android)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}