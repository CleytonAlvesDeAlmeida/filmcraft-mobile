plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.filmcraft.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.filmcraft.mobile"
        // Android 10+: MediaStore sem permissão de armazenamento para salvar em Filmes/.
        minSdk = 29
        targetSdk = 35
        // O Codemagic define BUILD_NUMBER; localmente vira 1.
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"
        // ABIs que o script de build do Rust gera.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // Assinatura de release: o Codemagic injeta estas variáveis quando há um keystore configurado.
    signingConfigs {
        create("release") {
            val path = System.getenv("CM_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("CM_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-common:$media3")
}
