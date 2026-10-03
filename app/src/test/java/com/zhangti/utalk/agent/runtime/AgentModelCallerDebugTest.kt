package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.context.AgentContextSnapshot
import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.AssembledContext
import com.zhangti.utalk.agent.context.ContextAssembler
import com.zhangti.utalk.agent.context.ContextPipeline
import com.zhangti.utalk.agent.context.ModelContextPolicy
import com.zhangti.utalk.agent.context.ToolResultContext
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.llm.LlmClient
import com.zhangti.utalk.agent.llm.LlmRequest
import com.zhangti.utalk.agent.llm.LlmStream
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AgentModelCallerDebugTest {
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
            ContextPipeline(listOf(ModelContextPolicy(softBudget = 1, maxToolResultChars = 3))))) { recorded = it }
        caller.stream(snapshot).close()
        assertSame(sent, recorded!!.request)
        assertEquals(2, recorded!!.projection.removedByType.values.sum())
        assertEquals(listOf("call"), recorded!!.projection.truncatedToolCallIds)
        assertEquals("abcdef", (snapshot.items.last() as ToolResultContext).content)
    }
}
