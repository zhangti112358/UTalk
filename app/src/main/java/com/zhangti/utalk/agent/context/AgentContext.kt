package com.zhangti.utalk.agent.context

import com.zhangti.utalk.agent.llm.LlmToolCall

/**
 * Agent 上下文的统一元素。
 *
 * 消息、工具结果、系统提示词和本轮可用工具都属于上下文，但保持独立类型，便于以后分别
 * 做对话压缩、工具结果压缩和工具定义裁剪。
 */
sealed interface AgentContextItem

data class SystemPromptContext(val text: String) : AgentContextItem

data class UserInputContext(val text: String) : AgentContextItem

data class AssistantOutputContext(
    val text: String?,
    val toolCalls: List<LlmToolCall> = emptyList(),
) : AgentContextItem

data class ToolResultContext(
    val toolCallId: String,
    val toolName: String,
    val content: String,
    val isError: Boolean,
) : AgentContextItem

/** 某个工具从这一项开始对后续模型请求可见。 */
data class ToolAvailabilityContext(
    val toolId: String,
    val source: ToolAvailabilitySource,
) : AgentContextItem

enum class ToolAvailabilitySource {
    CORE,
    DISCOVERED,
}

data class AgentContextSnapshot(val items: List<AgentContextItem>)

