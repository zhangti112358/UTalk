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
import org.junit.Assert.assertTrue
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

    @Test
    fun `train ticket and 12306 queries prioritize ticket tools`() = runBlocking {
        for (query in listOf("查询北京到上海的火车票", "12306")) {
            val context = InMemoryAgentContextStore()
            val catalog = ToolCatalog().apply {
                listOf(
                    "get-current-date", "get-stations-code-in-city", "get-station-code-of-citys",
                    "get-station-code-by-names", "get-station-by-telecode", "get-tickets",
                    "get-interline-tickets", "get-train-route-stations",
                ).forEach { name ->
                    val id = "rail__$name"
                    register(CatalogTool(
                        ToolMetadata(
                            id = id,
                            provider = "12306 火车票",
                            domain = ToolDomain.RAIL,
                            summary = if (name == "get-tickets") "查询12306余票信息" else name,
                            keywords = ToolDomain.RAIL.aliases,
                        ),
                        RenamedEchoTool(id),
                    ))
                }
            }

            val result = SearchToolsTool(catalog, context).call(mapOf("query" to query))
            assertFalse(result.isError == true)
            val active = context.snapshot().items.filterIsInstance<ToolAvailabilityContext>().map { it.toolId }
            assertTrue("$query 应召回直达余票工具", "rail__get-tickets" in active)
            assertTrue("$query 应召回中转余票工具", "rail__get-interline-tickets" in active)
        }
    }
}

private class RenamedEchoTool(name: String) : com.zhangti.utalk.agent.tool.AgentTool {
    private val echo = EchoTool()
    override val definition = echo.definition.copy(name = name)
    override suspend fun call(arguments: Map<String, Any?>) = echo.call(arguments)
}

