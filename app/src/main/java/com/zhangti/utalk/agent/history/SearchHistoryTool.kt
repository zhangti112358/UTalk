package com.zhangti.utalk.agent.history

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 按需检索手机本地历史；不会自动把旧会话塞回模型上下文。 */
class SearchHistoryTool(private val history: HistoryStore) : AgentTool {
    override val definition = Tool(
        name = ID,
        description = "搜索本机以前的对话记录。用户提到过去讨论过的事、昨天/刚才的旧会话时调用；支持关键词和时间范围，返回原始对话片段。不搜索当前地点或实时网络信息。",
        inputSchema = ToolSchema(
            properties = JsonObject(mapOf(
                "query" to field("关键词或短语；按原文匹配。只按时间搜索时可留空"),
                "after" to field("可选，起始时间，ISO-8601 格式，含时区，如 2026-10-01T00:00:00+08:00"),
                "before" to field("可选，结束时间，ISO-8601 格式，含时区"),
            )),
        ),
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult = try {
        val query = arguments["query"]?.toString().orEmpty().trim()
        val after = parseTime(arguments["after"])
        val before = parseTime(arguments["before"])
        require(query.isNotBlank() || after != null || before != null) { "请提供关键词或时间范围" }
        val matches = history.search(query, after, before)
        val text = if (matches.isEmpty()) "本机历史中没有找到匹配的对话。" else buildString {
            appendLine("本机历史对话（原文摘录，按时间倒序）：")
            matches.forEachIndexed { index, match ->
                val date = DATE_FORMAT.format(Instant.ofEpochMilli(match.occurredAtMillis))
                append("${index + 1}. $date；记录 ${match.sessionId.take(8)} / 第 ${match.turn} 轮")
                if (match.latitude != null && match.longitude != null) {
                    append(String.format(Locale.US, "；位置约 %.5f,%.5f", match.latitude, match.longitude))
                    match.accuracyMeters?.let { append("（精度约 ${it.toInt()} 米）") }
                }
                appendLine()
                appendLine(match.conversation)
            }
        }.trimEnd()
        CallToolResult(content = listOf(TextContent(text)), isError = false)
    } catch (error: Exception) {
        CallToolResult(content = listOf(TextContent(error.message ?: "历史搜索失败")), isError = true)
    }

    private fun parseTime(raw: Any?): Long? = raw?.toString()?.takeIf(String::isNotBlank)?.let {
        java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()
    }

    companion object {
        const val ID = "search_conversation_history"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
        private fun field(description: String) = JsonObject(mapOf(
            "type" to JsonPrimitive("string"), "description" to JsonPrimitive(description),
        ))
    }
}
