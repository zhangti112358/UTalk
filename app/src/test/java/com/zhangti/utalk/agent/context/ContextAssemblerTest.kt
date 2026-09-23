package com.zhangti.utalk.agent.context

import com.zhangti.utalk.agent.llm.LlmRole
import com.zhangti.utalk.agent.tool.EchoTool
import com.zhangti.utalk.agent.tool.catalog.CatalogTool
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import com.zhangti.utalk.agent.tool.catalog.ToolDomain
import com.zhangti.utalk.agent.tool.catalog.ToolMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextAssemblerTest {
    @Test
    fun `assembles messages and active tools independently`() {
        val catalog = ToolCatalog().apply {
            register(
                CatalogTool(
                    ToolMetadata("echo", "local", ToolDomain.SYSTEM, "echo"),
                    EchoTool(),
                )
            )
        }
        val store = InMemoryAgentContextStore()
        store.append(SystemPromptContext("system"))
        store.append(ToolAvailabilityContext("echo", ToolAvailabilitySource.CORE))
        store.append(UserInputContext("hello"))

        val request = ContextAssembler(catalog).assemble(store.snapshot())

        assertEquals(listOf(LlmRole.SYSTEM, LlmRole.USER), request.messages.map { it.role })
        assertEquals(listOf("echo"), request.tools.map { it.name })
        assertEquals("object", request.tools.single().schema["type"])
    }

    @Test
    fun `playback interruption becomes a model-visible context note`() {
        val store = InMemoryAgentContextStore()
        store.append(AssistantPlaybackContext("前半句", "前半句和后半句"))

        val request = ContextAssembler(ToolCatalog()).assemble(store.snapshot())

        assertEquals(LlmRole.SYSTEM, request.messages.single().role)
        assert(request.messages.single().content!!.contains("前半句"))
        assert(request.messages.single().content!!.contains("没有听到剩余部分"))
    }
}
