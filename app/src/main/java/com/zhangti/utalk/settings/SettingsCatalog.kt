package com.zhangti.utalk.settings

import com.zhangti.utalk.KeyNames
import java.io.StringReader
import java.util.Properties

data class ApiKeyField(val key: String, val label: String)

object SettingsCatalog {
    val apiKeys = listOf(
        ApiKeyField(KeyNames.DEEPSEEK, "DeepSeek 模型"),
        ApiKeyField(KeyNames.DOUBAO, "豆包语音 ASR / TTS"),
        ApiKeyField(KeyNames.AMAP, "高德地图"),
        ApiKeyField(KeyNames.DIDI, "滴滴出行"),
        ApiKeyField(KeyNames.VARIFLIGHT, "飞友航班"),
        ApiKeyField(KeyNames.CAIYUN_WEATHER, "彩云天气"),
        ApiKeyField(KeyNames.DIDA, "DIDA 酒店"),
    )
    /** 与根目录 secrets.properties 一样按 Java Properties 规则解析；未知项不会进入设置。 */
    fun parseImport(text: String): Map<String, String> {
        val cleaned = text.lineSequence().filterNot { it.trim().startsWith("```") }.joinToString("\n")
        val values = Properties().apply { load(StringReader(cleaned)) }
        return apiKeys.mapNotNull { field ->
            values.getProperty(field.key)?.trim()?.takeIf(String::isNotEmpty)?.let { field.key to it }
        }.toMap()
    }
}

enum class DiDiEnvironment { SANDBOX, PRODUCTION }
