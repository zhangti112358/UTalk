package com.zhangti.utalk

/**
 * 所有本地密钥的名字集中在这里，与 secrets.properties 里的 key 一一对应。
 * 新增 key：secrets.properties 加一行 + 这里加一个常量 + AppConfig 加一个字段。
 */
object KeyNames {
    /** DeepSeek（LLM）API Key */
    const val DEEPSEEK = "deepseek"

    /** 豆包 Doubao（语音 ASR/TTS）API Key */
    const val DOUBAO = "doubao"
}

/**
 * 应用配置数据类，统一入口。
 *
 * - 密钥：从本地 secrets.properties（已 gitignore）读取；
 * - 非敏感配置（链接、模型名等）：直接硬编码在下方。
 *
 * 用法：AppConfig.instance.deepseekApiKey / .deepseekBaseUrl
 *
 * 未来做「用户自己输入」时：load() 里先查用户配置（DataStore）、查不到再回落本地值，
 * 优先级：用户输入 > 本地构建值。
 */
data class AppConfig(
    val deepseekApiKey: String,
    val doubaoApiKey: String,
) {
    // ── 非敏感配置，硬编码 ──
    val deepseekBaseUrl: String = "https://api.deepseek.com/v1/"
    val deepseekModel: String = "deepseek-v4-flash"
    val doubaoAsrUrl: String = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
    val doubaoTtsUrl: String = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"

    companion object {
        /** 全局实例（直接获取用） */
        val instance: AppConfig by lazy { load() }

        /** 从本地注入的密钥构建配置 */
        fun load(): AppConfig = AppConfig(
            deepseekApiKey = LocalConfig[KeyNames.DEEPSEEK],
            doubaoApiKey = LocalConfig[KeyNames.DOUBAO],
        )
    }
}
