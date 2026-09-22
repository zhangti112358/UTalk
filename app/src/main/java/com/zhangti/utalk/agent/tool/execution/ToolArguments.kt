package com.zhangti.utalk.agent.tool.execution

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object ToolArguments {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(arguments: String): Map<String, Any?> {
        if (arguments.isBlank()) return emptyMap()
        val element = json.parseToJsonElement(arguments)
        require(element is JsonObject) { "工具参数必须是 JSON object" }
        return element.entries.associate { (key, value) -> key to value.toValue() }
    }

    private fun JsonElement.toValue(): Any? = when (this) {
        JsonNull -> null
        is JsonObject -> entries.associate { (key, value) -> key to value.toValue() }
        is JsonArray -> map { it.toValue() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
    }
}

