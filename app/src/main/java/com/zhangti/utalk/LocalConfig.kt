package com.zhangti.utalk

import org.json.JSONObject

/**
 * 本地密钥的低层读取器。
 *
 * 来源：项目根目录 secrets.properties（已 gitignore）的全部 key=value 条目，
 * 构建时注入 BuildConfig.LOCAL_CONFIG_JSON。
 *
 * 上层请用 [AppConfig] 数据类做类型化访问，不要直接调用这里。
 * 未来做「用户自己输入」时：在 AppConfig 里先查用户配置（DataStore）、查不到再回落本地值。
 */
object LocalConfig {

    private val values: Map<String, String> by lazy {
        val json = BuildConfig.LOCAL_CONFIG_JSON
        if (json.isBlank()) {
            emptyMap()
        } else {
            runCatching {
                val obj = JSONObject(json)
                buildMap {
                    obj.keys().forEach { put(it, obj.optString(it, "")) }
                }
            }.getOrDefault(emptyMap())
        }
    }

    /** 按名字取配置，如 LocalConfig["deepseek"]；没有则返回空串 */
    operator fun get(name: String): String = values[name].orEmpty()
}
