package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.context.AgentContextStore
import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.ToolResultContext
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.tool.execution.ToolArguments
import com.zhangti.utalk.agent.tool.execution.ToolInvoker
import com.zhangti.utalk.agent.tool.execution.ToolResultFormatter
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking

/** 模型 → 工具 → 模型的循环；不负责 UI、工具发现策略或上下文存储实现。 */
class AgentLoop(
    private val context: AgentContextStore,
    private val model: AgentModelCaller,
    private val tools: ToolInvoker,
    private val maxModelCallsPerTurn: Int = 8,
) {
    fun runTurn(
        userText: String,
        listener: AgentEventListener,
        cancellation: AgentCancellation = AgentCancellation(),
    ) {
        context.append(UserInputContext(userText))
        var lastText = ""
        try {
            repeat(maxModelCallsPerTurn) {
                if (cancellation.isCancelled) return cancelled(listener)
                val stream = model.stream(context.snapshot())
                cancellation.attach(stream)
                val text = StringBuilder()
                val toolCalls = ToolCallAccumulator()
                try {
                    while (!cancellation.isCancelled) {
                        val chunk = stream.next() ?: break
                        chunk.text?.takeIf(String::isNotEmpty)?.let {
                            text.append(it)
                            listener.onEvent(AgentEvent.TextDelta(it))
                        }
                        toolCalls.add(chunk.toolCalls)
                    }
                } finally {
                    cancellation.detach(stream)
                    stream.close()
                }
                if (cancellation.isCancelled) return cancelled(listener)

                val calls = toolCalls.build()
                val assistantText = text.toString().ifBlank { null }
                context.append(AssistantOutputContext(assistantText, calls))
                if (assistantText != null) lastText = assistantText
                if (calls.isEmpty()) {
                    listener.onEvent(AgentEvent.Completed(lastText))
                    return
                }

                calls.forEach { call ->
                    if (cancellation.isCancelled) return cancelled(listener)
                    listener.onEvent(AgentEvent.ToolStarted(call.name))
                    val result = parseAndInvoke(call.name, call.arguments)
                    context.append(
                        ToolResultContext(
                            toolCallId = call.id,
                            toolName = call.name,
                            content = ToolResultFormatter.format(result),
                            isError = result.isError == true,
                        )
                    )
                    listener.onEvent(AgentEvent.ToolFinished(call.name, result.isError == true))
                }
            }
            listener.onEvent(AgentEvent.Failed("本轮工具调用次数过多，已停止"))
        } catch (t: Throwable) {
            if (cancellation.isCancelled) cancelled(listener)
            else listener.onEvent(AgentEvent.Failed(errorMessage(t)))
        }
    }

    private fun parseAndInvoke(name: String, rawArguments: String): CallToolResult = runBlocking {
        val arguments = runCatching { ToolArguments.parse(rawArguments) }
            .getOrElse {
                return@runBlocking CallToolResult(
                    content = listOf(TextContent("工具参数 JSON 无效：${it.message}")),
                    isError = true,
                )
            }
        tools.invoke(name, arguments)
    }

    private fun cancelled(listener: AgentEventListener) {
        listener.onEvent(AgentEvent.Cancelled)
    }

    private fun errorMessage(throwable: Throwable): String {
        var cause = throwable
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        return cause.message ?: throwable.message ?: "Agent 运行失败"
    }
}
