package com.zhangti.utalk.agent.tool

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool

/**
 * 工具注册表：按名注册/查询/调用，对调用方屏蔽工具来源（本地 or 远程 MCP）。
 */
class ToolRegistry {

    private val tools = LinkedHashMap<String, AgentTool>()

    /** 已注册的工具名集合。 */
    val names: Set<String> get() = tools.keys

    /** 注册工具；重名抛 [IllegalArgumentException]。 */
    fun register(tool: AgentTool): AgentTool {
        require(tool.definition.name !in tools) { "工具重名：${tool.definition.name}" }
        tools[tool.definition.name] = tool
        return tool
    }

    /** 批量注册（如远程 MCP server 的 listTools 结果）。 */
    fun registerAll(toolList: List<AgentTool>) {
        toolList.forEach { register(it) }
    }

    /** 全部工具定义（供 LLM / MCP server 使用）。 */
    fun definitions(): List<Tool> = tools.values.map { it.definition }

    /** 按名取工具，不存在返回 null。 */
    operator fun get(name: String): AgentTool? = tools[name]

    /**
     * 按名调用工具。
     * 工具不存在时返回 isError=true 的结果而非抛异常，便于 LLM 看到并修正。
     */
    suspend fun call(name: String, arguments: Map<String, Any?>): CallToolResult {
        val tool = tools[name]
            ?: return CallToolResult(content = listOf(TextContent("未知工具：$name")), isError = true)
        return tool.call(arguments)
    }
}
