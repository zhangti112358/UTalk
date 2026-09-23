package com.zhangti.utalk.agent.context

import com.zhangti.utalk.agent.llm.LlmMessage
import com.zhangti.utalk.agent.llm.LlmRequest
import com.zhangti.utalk.agent.llm.LlmRole
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import com.zhangti.utalk.agent.tool.model.ToolDefinitionAdapter

/** 把厂商无关的 Agent 上下文组装成一次 LLM 请求。 */
class ContextAssembler(
    private val catalog: ToolCatalog,
    private val pipeline: ContextPipeline = ContextPipeline(),
) {
    fun assemble(snapshot: AgentContextSnapshot): LlmRequest {
        val context = pipeline.apply(snapshot)
        val messages = context.items.mapNotNull { item ->
            when (item) {
                is SystemPromptContext -> LlmMessage(LlmRole.SYSTEM, item.text)
                is UserInputContext -> LlmMessage(LlmRole.USER, item.text)
                is AssistantOutputContext -> LlmMessage(
                    role = LlmRole.ASSISTANT,
                    content = item.text,
                    toolCalls = item.toolCalls,
                )
                is ToolResultContext -> LlmMessage(
                    role = LlmRole.TOOL,
                    content = item.content,
                    toolCallId = item.toolCallId,
                )
                is ToolAvailabilityContext -> null
                is AssistantPlaybackContext -> LlmMessage(
                    role = LlmRole.SYSTEM,
                    content = buildString {
                        append("上一轮语音播报被用户打断。用户实际听到的内容")
                        if (item.estimated) append("（根据音频进度估算）")
                        append("：\"")
                        append(item.spokenPrefix)
                        append("\"。上一轮完整回答是：\"")
                        append(item.fullResponse)
                        append("\"。回答下一轮时请考虑用户没有听到剩余部分，不要假设其已知。")
                    },
                )
            }
        }
        val toolIds = context.items.filterIsInstance<ToolAvailabilityContext>()
            .map { it.toolId }
            .distinct()
        val tools = toolIds.mapNotNull(catalog::get)
            .map { ToolDefinitionAdapter.toLlmTool(it.tool.definition) }
        return LlmRequest(messages = messages, tools = tools)
    }
}
