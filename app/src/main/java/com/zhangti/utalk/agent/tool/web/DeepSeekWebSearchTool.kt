package com.zhangti.utalk.agent.tool.web

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject

/** 使用 DeepSeek 自研搜索引擎查询实时网页。 */
class DeepSeekWebSearchTool(private val api: DeepSeekWebService) : AgentTool {
    override val definition = Tool(
        name = ID,
        description = "搜索互联网，获取实时网页结果。适合新闻、近期变化、事实核验和查找网页；返回标题、URL、摘要、日期与权威性。通常先搜索，再用 web_fetch 阅读最相关页面。",
        inputSchema = ToolSchema(
            properties = JsonObject(mapOf(
                "query" to stringField("搜索词，1到128个字符；支持 site:、lang:、after:、before: 等搜索语法"),
                "count" to integerField("可选，返回条数，1到50；一般使用5条即可"),
                "ori_question" to stringField("可选，触发搜索的用户原始问题，1到128个字符"),
                "localization" to stringField("可选，用于优先返回本地结果的IP地址"),
                "need_content" to booleanField("可选，是否同时返回网页正文；通常为false，需要全文时改用web_fetch"),
            )),
            required = listOf("query"),
        ),
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult = toolResult {
        val query = arguments["query"]?.toString()?.trim().orEmpty()
        require(query.length in 1..128) { "query 必须为1到128个字符" }
        val body = JSONObject().put("query", query)
        arguments["count"]?.let {
            val count = (it as? Number)?.toInt() ?: it.toString().toIntOrNull()
            require(count != null && count in 1..50) { "count 必须为1到50的整数" }
            body.put("count", count)
        }
        optionalString(arguments, body, "ori_question", maxLength = 128)
        optionalString(arguments, body, "localization")
        arguments["need_content"]?.let { body.put("need_content", parseBoolean(it, "need_content")) }
        api.search(body).toString(2)
    }

    companion object {
        const val ID = "web_search"
    }
}

internal fun toolResult(block: () -> String): CallToolResult = try {
    CallToolResult(content = listOf(TextContent(block())), isError = false)
} catch (error: Exception) {
    CallToolResult(content = listOf(TextContent(error.message ?: "网页工具调用失败")), isError = true)
}

internal fun optionalString(arguments: Map<String, Any?>, body: JSONObject, name: String, maxLength: Int? = null) {
    val value = arguments[name]?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return
    require(maxLength == null || value.length <= maxLength) { "$name 不能超过${maxLength}个字符" }
    body.put(name, value)
}

internal fun parseBoolean(value: Any, name: String): Boolean = when (value) {
    is Boolean -> value
    else -> value.toString().toBooleanStrictOrNull() ?: error("$name 必须为布尔值")
}

internal fun stringField(description: String) = JsonObject(mapOf(
    "type" to JsonPrimitive("string"), "description" to JsonPrimitive(description),
))

private fun integerField(description: String) = JsonObject(mapOf(
    "type" to JsonPrimitive("integer"), "description" to JsonPrimitive(description),
    "minimum" to JsonPrimitive(1), "maximum" to JsonPrimitive(50),
))

private fun booleanField(description: String) = JsonObject(mapOf(
    "type" to JsonPrimitive("boolean"), "description" to JsonPrimitive(description),
))
