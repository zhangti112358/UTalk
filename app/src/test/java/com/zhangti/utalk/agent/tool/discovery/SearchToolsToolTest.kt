package com.zhangti.utalk.agent.tool.discovery

import com.zhangti.utalk.agent.context.InMemoryAgentContextStore
import com.zhangti.utalk.agent.context.ToolAvailabilityContext
import com.zhangti.utalk.agent.tool.EchoTool
import com.zhangti.utalk.agent.tool.catalog.CatalogTool
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import com.zhangti.utalk.agent.tool.catalog.ToolDomain
import com.zhangti.utalk.agent.tool.catalog.ToolMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchToolsToolTest {
    @Test
    fun `search activates matching tool in context`() = runBlocking {
        val context = InMemoryAgentContextStore()
        val catalog = ToolCatalog().apply {
            register(
                CatalogTool(
                    ToolMetadata(
                        id = "weather__echo",
                        provider = "weather",
                        domain = ToolDomain.WEATHER,
                        summary = "查询实时天气",
                    ),
                    RenamedEchoTool("weather__echo"),
                )
            )
        }

        val result = SearchToolsTool(catalog, context).call(
            mapOf("query" to "查询天气", "domain" to "weather")
        )

        assertFalse(result.isError == true)
        val active = context.snapshot().items.filterIsInstance<ToolAvailabilityContext>()
        assertEquals(listOf("weather__echo"), active.map { it.toolId })
    }
}

private class RenamedEchoTool(name: String) : com.zhangti.utalk.agent.tool.AgentTool {
    private val echo = EchoTool()
    override val definition = echo.definition.copy(name = name)
    override suspend fun call(arguments: Map<String, Any?>) = echo.call(arguments)
}

