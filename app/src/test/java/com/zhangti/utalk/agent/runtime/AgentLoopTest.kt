package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.ContextAssembler
import com.zhangti.utalk.agent.context.InMemoryAgentContextStore
import com.zhangti.utalk.agent.context.ToolResultContext
import com.zhangti.utalk.agent.llm.LlmChunk
import com.zhangti.utalk.agent.llm.LlmClient
import com.zhangti.utalk.agent.llm.LlmRequest
import com.zhangti.utalk.agent.llm.LlmStream
import com.zhangti.utalk.agent.llm.LlmToolCallDelta
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import com.zhangti.utalk.agent.tool.execution.ToolInvoker
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    @Test
    fun `photo is appended after all tool results and sent on next turn`() {
        val context = InMemoryAgentContextStore()
        val requests = mutableListOf<LlmRequest>()
        var index = 0
        val llm = object : LlmClient {
            override fun stream(request: LlmRequest): LlmStream {
                requests += request
                val chunk = if (index++ == 0) LlmChunk(null, listOf(
                    LlmToolCallDelta(0, "photo-1", "device_take_photo", "{}"),
                    LlmToolCallDelta(1, "time-1", "device_current_time", "{}"),
                )) else LlmChunk("这是花。")
                var emitted = false
                return object : LlmStream {
                    override fun next(): LlmChunk? = if (emitted) null else chunk.also { emitted = true }
                    override fun close() = Unit
                }
            }
        }
        val photo = com.zhangti.utalk.agent.tool.local.TakePhotoTool {
            com.zhangti.utalk.agent.tool.local.CapturedPhoto("/private/photo.jpg", "now", "wide")
        }
        val loop = AgentLoop(context, AgentModelCaller(llm, ContextAssembler(ToolCatalog())),
            ToolInvoker { name, _, _ ->
                if (name == "device_take_photo") photo.call(emptyMap()) else CallToolResult(listOf(TextContent("now")))
            })
        loop.runTurn("看一下花", AgentEventListener {})
        loop.runTurn("它是什么颜色", AgentEventListener {})
        assertEquals("/private/photo.jpg", requests.last().messages.flatMap { it.images }.single().path)
        val messages = requests[1].messages
        assertEquals(listOf(com.zhangti.utalk.agent.llm.LlmRole.TOOL, com.zhangti.utalk.agent.llm.LlmRole.TOOL),
            messages.drop(2).take(2).map { it.role })
        assertEquals(com.zhangti.utalk.agent.llm.LlmRole.USER, messages[4].role)
    }

    @Test
    fun `loops through tool result and returns final answer`() {
        val context = InMemoryAgentContextStore()
        val llm = QueueLlmClient(
            listOf(
                listOf(
                    LlmChunk(null, listOf(LlmToolCallDelta(0, "call-1", "weather", "{\"city\":"))),
                    LlmChunk(null, listOf(LlmToolCallDelta(0, arguments = "\"北京\"}")), "tool_calls"),
                ),
                listOf(LlmChunk("北京晴朗。", finishReason = "stop")),
            )
        )
        val events = mutableListOf<AgentEvent>()
        val invoker = ToolInvoker { name, arguments, _ ->
            assertEquals("weather", name)
            assertEquals("北京", arguments["city"])
            CallToolResult(listOf(TextContent("晴，25℃")), isError = false)
        }
        val loop = AgentLoop(
            context,
            AgentModelCaller(llm, ContextAssembler(ToolCatalog())),
            invoker,
        )

        loop.runTurn("北京天气", AgentEventListener(events::add))

        assertTrue(context.snapshot().items.any { it is ToolResultContext })
        assertTrue(context.snapshot().items.filterIsInstance<AssistantOutputContext>().size == 2)
        assertEquals("北京晴朗。", (events.last() as AgentEvent.Completed).text)
    }
}

private class QueueLlmClient(responses: List<List<LlmChunk>>) : LlmClient {
    private val queue = ArrayDeque(responses)
    override fun stream(request: LlmRequest): LlmStream {
        val chunks = ArrayDeque(queue.removeFirst())
        return object : LlmStream {
            override fun next(): LlmChunk? = chunks.removeFirstOrNull()
            override fun close() = Unit
        }
    }
}
