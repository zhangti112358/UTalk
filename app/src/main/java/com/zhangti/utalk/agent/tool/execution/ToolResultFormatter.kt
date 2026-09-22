package com.zhangti.utalk.agent.tool.execution

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

object ToolResultFormatter {
    fun format(result: CallToolResult): String {
        val parts = result.content.map { content ->
            when (content) {
                is TextContent -> content.text
                else -> content.toString()
            }
        }.toMutableList()
        result.structuredContent?.let { parts += it.toString() }
        return parts.joinToString("\n").ifBlank { "工具执行完成，但没有返回内容" }
    }
}

