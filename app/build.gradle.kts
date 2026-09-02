import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 从 secrets.properties（已 gitignore）读取本地密钥/配置，构建时注入 BuildConfig
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val localConfigJson = secrets.stringPropertyNames()
    .joinToString(",", "{", "}") { name ->
        val value = secrets.getProperty(name).orEmpty()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        "\"$name\":\"$value\""
    }

android {
    namespace = "com.zhangti.utalk"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.zhangti.utalk"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // 本地配置 JSON（来自 secrets.properties 的全部条目）
        buildConfigField(
            "String",
            "LOCAL_CONFIG_JSON",
            "\"${localConfigJson.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // onnx 模型不压缩，便于直接读取
    androidResources {
        noCompress += "onnx"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    // Silero VAD 推理引擎（speech/vad）
    implementation(libs.onnxruntime.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}