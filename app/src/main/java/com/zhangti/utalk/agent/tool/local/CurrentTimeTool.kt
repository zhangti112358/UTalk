package com.zhangti.utalk.agent.tool.local

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/** 一次调用同时返回手机本地日期、星期和时间。 */
class CurrentTimeTool(
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) : AgentTool {
    override val definition = Tool(
        name = ID,
        description = "获取手机当前时间，返回年月日、星期几和几点。",
        inputSchema = ToolSchema(properties = JsonObject(emptyMap())),
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult {
        val time = now()
        val weekday = WEEKDAYS[time.dayOfWeek.value - 1]
        val text = String.format(
            Locale.CHINA,
            "手机当前时间：%d年%d月%d日 星期%s %02d:%02d:%02d",
            time.year, time.monthValue, time.dayOfMonth, weekday,
            time.hour, time.minute, time.second,
        )
        return CallToolResult(content = listOf(TextContent(text)), isError = false)
    }

    companion object {
        const val ID = "device_current_time"
        private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
    }
}
