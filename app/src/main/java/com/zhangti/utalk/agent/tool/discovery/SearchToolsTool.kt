package com.zhangti.utalk.agent.tool.discovery

import com.zhangti.utalk.agent.context.AgentContextStore
import com.zhangti.utalk.agent.context.ToolAvailabilityContext
import com.zhangti.utalk.agent.context.ToolAvailabilitySource
import com.zhangti.utalk.agent.tool.AgentTool
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import com.zhangti.utalk.agent.tool.catalog.ToolDomain
import com.zhangti.utalk.agent.tool.catalog.ToolRisk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 搜索低频工具，并把命中的完整定义激活到当前会话上下文。 */
class SearchToolsTool(
    private val catalog: ToolCatalog,
    private val context: AgentContextStore,
) : AgentTool {
    override val definition = Tool(
        name = ID,
        description = "搜索并加载当前未直接展示的出行工具。需要查询航班、机票、酒店、天气、打车或地图扩展能力时先调用。搜索结果中的工具会自动在下一轮可用。",
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "query" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("用自然语言描述需要的能力，如：查询未来三天天气"),
                        )
                    ),
                    "domain" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("可选领域：map、flight、hotel、ride、weather"),
                        )
                    ),
                )
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult {
        val query = arguments["query"]?.toString().orEmpty()
        if (query.isBlank()) return CallToolResult(
            content = listOf(TextContent("query 不能为空")),
            isError = true,
        )
        val domain = parseDomain(arguments["domain"]?.toString())
        val matches = catalog.search(query, domain, limit = 5)
        matches.forEach {
            context.append(
                ToolAvailabilityContext(
                    toolId = it.metadata.id,
                    source = ToolAvailabilitySource.DISCOVERED,
                )
            )
        }
        val text = if (matches.isEmpty()) {
            "没有找到匹配工具。请换一种能力描述，或直接向用户说明当前不支持。"
        } else {
            buildString {
                appendLine("已加载以下工具，下一轮可以直接调用：")
                matches.forEach {
                    append("- ${it.metadata.id}: ${it.metadata.summary}")
                    when (it.metadata.risk) {
                        ToolRisk.READ_ONLY -> Unit
                        ToolRisk.EXPLICIT_RIDE_ORDER -> append("（仅在用户本轮明确要求叫车时可执行）")
                        ToolRisk.BLOCKED -> append("（当前应用暂不执行）")
                    }
                    appendLine()
                }
            }.trimEnd()
        }
        return CallToolResult(content = listOf(TextContent(text)), isError = false)
    }

    private fun parseDomain(value: String?): ToolDomain? = when (value?.lowercase()) {
        "map", "地图" -> ToolDomain.MAP
        "flight", "航班", "机票" -> ToolDomain.FLIGHT
        "hotel", "酒店" -> ToolDomain.HOTEL
        "ride", "taxi", "打车" -> ToolDomain.RIDE
        "weather", "天气" -> ToolDomain.WEATHER
        else -> null
    }

    companion object {
        const val ID = "search_tools"
    }
}
