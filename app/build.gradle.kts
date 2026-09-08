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
    testOptions {
        // JVM 单测里访问 android.util.Log 等 stub 时静默返回，而不是抛异常
        unitTests.isReturnDefaultValues = true
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
    // 豆包 ASR WebSocket 客户端（speech/asr）
    implementation(libs.okhttp)
    // MCP 协议类型与客户端（agent/tool 统一工具抽象 + 远程 MCP 调用）
    implementation(libs.mcp.kotlin.sdk.core)
    implementation(libs.mcp.kotlin.sdk.client)
    // Ktor HTTP 引擎（MCP Streamable HTTP 传输需要，SDK 不内置引擎）
    implementation(libs.ktor.client.okhttp)
    // JVM 单测里 org.json 是 Android stub（keys() 返回 null），用真实实现替代
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}