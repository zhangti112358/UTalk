package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.context.AgentContextSnapshot
import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.AssembledContext
import com.zhangti.utalk.agent.context.ContextAssembler
import com.zhangti.utalk.agent.context.ContextPipeline
import com.zhangti.utalk.agent.context.ModelContextPolicy
import com.zhangti.utalk.agent.context.ContextTransformer
import com.zhangti.utalk.agent.context.ToolResultContext
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.llm.LlmClient
import com.zhangti.utalk.agent.llm.LlmRequest
import com.zhangti.utalk.agent.llm.LlmStream
import com.zhangti.utalk.agent.llm.LlmUsage
import com.zhangti.utalk.agent.llm.LlmChunk
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class AgentModelCallerDebugTest {
    @Test fun usageIsObservedOnceAndDiagnosticFailuresDoNotBreakOutput() {
        val actual = LlmUsage(100, 10, 110)
        val chunks = ArrayDeque(listOf(LlmChunk("回答"), LlmChunk(null, usage = actual), LlmChunk(null, usage = actual)))
        val client = object : LlmClient {
            override fun stream(request: LlmRequest) = object : LlmStream {
                override fun next() = if (chunks.isEmpty()) null else chunks.removeFirst()
                override fun close() = Unit
            }
        }
        var observations = 0
        val caller = AgentModelCaller(client, ContextAssembler(ToolCatalog()), onUsage = {
            observations++; assertEquals(actual, it); error("模拟记录故障")
        })
        caller.stream(AgentContextSnapshot(listOf(UserInputContext("问题")))).use { stream ->
            assertEquals("回答", stream.next()!!.text)
            stream.next(); stream.next()
            assertNull(stream.next())
        }
        assertEquals(1, observations)
    }

    @Test fun recorderSeesExactlyTheRequestSentToClientAndItsProjection() {
        var recorded: AssembledContext? = null
        var sent: LlmRequest? = null
        val client = object : LlmClient {
            override fun stream(request: LlmRequest): LlmStream {
                sent = request
                return object : LlmStream { override fun next() = null; override fun close() = Unit }
            }
        }
        val snapshot = AgentContextSnapshot(listOf(
            UserInputContext("旧问题"), AssistantOutputContext("旧回答"),
            UserInputContext("当前问题"), ToolResultContext("call", "tool", "abcdef", false),
        ))
        val caller = AgentModelCaller(client, ContextAssembler(ToolCatalog(),
            ContextPipeline(listOf(ContextTransformer { original ->
                AgentContextSnapshot(original.items.drop(2).map {
                    if (it is ToolResultContext) it.copy(content = it.content.take(3)) else it
                })
            })))) { recorded = it }
        caller.stream(snapshot).close()
        assertSame(sent, recorded!!.request)
        assertEquals(2, recorded!!.projection.removedByType.values.sum())
        assertEquals(listOf("call"), recorded!!.projection.truncatedToolCallIds)
        assertEquals("abcdef", (snapshot.items.last() as ToolResultContext).content)
    }
}
