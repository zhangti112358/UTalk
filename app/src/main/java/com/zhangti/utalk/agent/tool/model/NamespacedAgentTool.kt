package com.zhangti.utalk.agent.tool.model

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult

/** 给远程工具添加稳定命名空间，同时保留对原始 MCP 工具的调用。 */
class NamespacedAgentTool(
    namespace: String,
    private val delegate: AgentTool,
) : AgentTool {
    override val definition = delegate.definition.copy(
        name = "${namespace}__${delegate.definition.name}",
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult =
        delegate.call(arguments)
}

