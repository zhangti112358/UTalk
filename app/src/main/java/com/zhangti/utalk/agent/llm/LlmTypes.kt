package com.zhangti.utalk.agent.llm

/**
 * LLM 调用相关类型。刻意不用任何具体 SDK 的类型，
 * 让上层（agent）与模型实现解耦：上下文、system prompt、工具注册均由上层管理，
 * 这里只负责「把消息和工具发给模型，把流式结果吐回来」。
 */

enum class LlmRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

/**
 * 一条对话消息。
 *
 * @param role 角色
 * @param content 文本内容（可空，如纯工具调用消息）
 * @param toolCalls 助手消息携带的工具调用（工具调用历史）
 * @param toolCallId role=TOOL 时对应工具调用的 id
 */
data class LlmMessage(
    val role: LlmRole,
    val content: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
)

/** 一次工具调用（arguments 为 JSON 字符串）。 */
data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** 工具定义（schema 为 JSON Schema 的 Map 表示）。 */
data class LlmTool(
    val name: String,
    val description: String,
    val schema: Map<String, Any?>,
)

/** 流式返回的一块内容。 */
data class LlmChunk(
    /** 增量文本（可能为 null，如纯工具调用块） */
    val text: String?,
    /** 增量工具调用（arguments 为分片，由上层累积） */
    val toolCalls: List<LlmToolCallDelta> = emptyList(),
    /** 结束原因（stop/tool_calls/length 等），仅在最后一块出现 */
    val finishReason: String? = null,
)

/** 流式工具调用分片：index 标识同一个调用，其余字段可能逐块补齐。 */
data class LlmToolCallDelta(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

/** 一次模型调用请求（上下文与工具由上层拼好传入）。 */
data class LlmRequest(
    val messages: List<LlmMessage>,
    val tools: List<LlmTool> = emptyList(),
    val temperature: Float? = null,
    val maxTokens: Int? = null,
)
