package com.zhangti.utalk.agent.tool.model

import com.zhangti.utalk.agent.llm.LlmTool
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object ToolDefinitionAdapter {
    private val json = Json {
        encodeDefaults = true
        // OpenAI/DeepSeek 会严格校验 JSON Schema；可选字段必须省略，不能输出 null。
        explicitNulls = false
    }

    @Suppress("UNCHECKED_CAST")
    fun toLlmTool(tool: Tool): LlmTool {
        val schema = (json.encodeToJsonElement(
            io.modelcontextprotocol.kotlin.sdk.types.ToolSchema.serializer(),
            tool.inputSchema,
        ).toAny() as? Map<String, Any?>).orEmpty()
        return LlmTool(
            name = tool.name,
            description = tool.description.orEmpty(),
            schema = schema,
        )
    }
}

internal fun JsonElement.toAny(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> entries.associate { (key, value) -> key to value.toAny() }
    is JsonArray -> map { it.toAny() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
}
