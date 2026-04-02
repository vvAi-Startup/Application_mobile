plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    kotlin("plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

android {
    namespace = "com.vvai.calmwave"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vvai.calmwave"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig fields from environment variables
        // Fallback padrão aponta para o backend em produção (Render)
        val apiBaseUrl = System.getenv("API_BASE_URL") ?: "https://calm-wave-backend.onrender.com"
        val wsBaseUrl = System.getenv("WS_BASE_URL") ?: "wss://calm-wave-backend.onrender.com"
        val dbBaseUrl = System.getenv("DB_BASE_URL") ?: "https://calm-wave-backend.onrender.com"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "WS_BASE_URL", "\"$wsBaseUrl\"")
        buildConfigField("String", "DB_BASE_URL", "\"$dbBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    // Não comprimir arquivos do modelo ONNX
    aaptOptions {
        noCompress("onnx")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // JSON processing library for Android
    implementation("org.json:json:20231013")
    
    // Room Database para cache offline
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // WorkManager para sincronização em background
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Retrofit para comunicação REST
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Gson para JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // DataStore para SharedPreferences modernos
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // SharedPreferences criptografadas para dados sensíveis
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Material icons (extended) - provides Icons.Filled.Pause / PlayArrow
    implementation("androidx.compose.material:material-icons-extended")
    // Accompanist System UI Controller para controlar status bar
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
    // ONNX Runtime para inferência local do modelo de denoising
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
    // ExoPlayer Media3 (versão mais recente recomendada)
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}