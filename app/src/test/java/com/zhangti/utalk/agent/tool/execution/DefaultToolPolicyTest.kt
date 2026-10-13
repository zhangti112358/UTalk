package com.zhangti.utalk.agent.tool.execution

import com.zhangti.utalk.agent.tool.AgentTool
import com.zhangti.utalk.agent.tool.catalog.CatalogTool
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import com.zhangti.utalk.agent.tool.catalog.ToolDomain
import com.zhangti.utalk.agent.tool.catalog.ToolMetadata
import com.zhangti.utalk.agent.tool.catalog.ToolRisk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultToolPolicyTest {
    @Test fun `ride order meaning is left to the agent`() = runBlocking {
        val fixture = fixture()

        for (userText in listOf("好", "下单", "不是", "只是看看价格", "")) {
            val turn = ToolTurnContext(userText)
            assertFalse(fixture.invoker.invoke(ORDER_TOOL, emptyMap(), turn).isError == true)
        }

        assertEquals(5, fixture.orderCalls())
    }

    @Test fun `a turn cannot create two orders`() = runBlocking {
        val fixture = fixture()
        val turn = ToolTurnContext("确认下单")

        assertFalse(fixture.invoker.invoke(ORDER_TOOL, emptyMap(), turn).isError == true)
        assertTrue(fixture.invoker.invoke(ORDER_TOOL, emptyMap(), turn).isError == true)
        assertEquals(1, fixture.orderCalls())
    }

    private data class Fixture(val invoker: CatalogToolInvoker, val orderCalls: () -> Int)

    private fun fixture(): Fixture {
        var calls = 0
        val catalog = ToolCatalog().apply {
            register(CatalogTool(
                ToolMetadata(ORDER_TOOL, "滴滴出行", ToolDomain.RIDE, ORDER_TOOL, ToolRisk.EXPLICIT_RIDE_ORDER),
                object : AgentTool {
                    override val definition = Tool(
                        name = ORDER_TOOL,
                        description = ORDER_TOOL,
                        inputSchema = ToolSchema(properties = JsonObject(emptyMap())),
                    )

                    override suspend fun call(arguments: Map<String, Any?>): CallToolResult {
                        calls++
                        return CallToolResult(content = listOf(TextContent("订单成功")), isError = false)
                    }
                },
            ))
        }
        return Fixture(CatalogToolInvoker(catalog)) { calls }
    }

    companion object {
        private const val ORDER_TOOL = "ride__taxi_create_order"
    }
}
