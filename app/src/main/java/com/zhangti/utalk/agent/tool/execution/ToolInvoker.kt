package com.zhangti.utalk.agent.tool.execution

import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

fun interface ToolInvoker {
    suspend fun invoke(toolId: String, arguments: Map<String, Any?>, turn: ToolTurnContext): CallToolResult
}

class CatalogToolInvoker(
    private val catalog: ToolCatalog,
    private val policy: ToolPolicy = ExplicitRideOrderPolicy(),
) : ToolInvoker {
    override suspend fun invoke(
        toolId: String,
        arguments: Map<String, Any?>,
        turn: ToolTurnContext,
    ): CallToolResult {
        val entry = catalog.get(toolId) ?: return error("未知或尚未加载的工具：$toolId")
        when (val decision = policy.evaluate(entry, arguments, turn)) {
            ToolPolicyDecision.Allow -> Unit
            is ToolPolicyDecision.Deny -> return error(decision.reason)
        }
        return runCatching { entry.tool.call(arguments) }
            .getOrElse { error("工具 $toolId 调用失败：${it.message ?: it.javaClass.simpleName}") }
    }

    private fun error(message: String) = CallToolResult(
        content = listOf(TextContent(message)),
        isError = true,
    )
}
