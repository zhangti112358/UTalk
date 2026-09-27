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
    fun `original photo remains in subsequent turns`() {
        val store = InMemoryAgentContextStore()
        store.append(UserInputContext("看一下这个花"))
        store.append(ImageContext("/private/photo.jpg", "2026-09-27T10:00:00Z"))
        store.append(AssistantOutputContext("这是一朵花。"))
        store.append(UserInputContext("花瓣有斑点吗"))

        val request = ContextAssembler(ToolCatalog()).assemble(store.snapshot())

        assertEquals("/private/photo.jpg", request.messages.flatMap { it.images }.single().path)
        assertEquals(LlmRole.USER, request.messages.single { it.images.isNotEmpty() }.role)
        assertEquals("花瓣有斑点吗", request.messages.last().content)
    }

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
