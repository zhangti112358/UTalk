package com.zhangti.utalk.agent.tool

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 极简示例工具：原样返回传入文本。
 * 用于验证统一工具抽象（本地工具 + 注册表 + 调用）可编译可运行。
 */
class EchoTool : AgentTool {

    override val definition = Tool(
        name = "echo",
        description = "原样返回传入的文本",
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "text" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("要回显的文本"),
                        )
                    )
                )
            ),
            required = listOf("text"),
        ),
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult =
        CallToolResult(content = listOf(TextContent(arguments["text"]?.toString() ?: "")))
}
