package com.zhangti.utalk.agent.tool

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool

/**
 * 统一工具抽象：本地函数封装与远程 MCP 服务的工具共用同一接口。
 *
 * 定义与调用结果直接使用 MCP 协议类型（io.modelcontextprotocol:kotlin-sdk-core），
 * 本地/远程工具对调用方完全无差别；同一份定义也可直接注册到 MCP server 对外暴露。
 */
interface AgentTool {

    /** 工具定义（name / description / inputSchema），供 LLM 与 MCP 服务端使用。 */
    val definition: Tool

    /**
     * 执行工具，返回给 LLM 的结果。
     * 注意：工具自身的失败应返回 isError=true 的 [CallToolResult]，而不是抛异常，
     * 这样 LLM 能看到错误信息并自我纠正。
     */
    suspend fun call(arguments: Map<String, Any?>): CallToolResult
}
