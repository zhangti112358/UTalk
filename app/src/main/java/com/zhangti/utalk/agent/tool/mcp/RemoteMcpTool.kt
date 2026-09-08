package com.zhangti.utalk.agent.tool.mcp

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

/**
 * 远程 MCP 工具：定义来自服务端 listTools，调用委托 MCP client；
 * 每次调用前先过 [RateLimiter]，遵守服务端 QPS 限制。
 */
class RemoteMcpTool(
    private val tool: Tool,
    private val client: Client,
    private val limiter: RateLimiter,
) : AgentTool {

    override val definition: Tool get() = tool

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult {
        limiter.acquire()
        return client.callTool(name = tool.name, arguments = arguments)
    }
}
