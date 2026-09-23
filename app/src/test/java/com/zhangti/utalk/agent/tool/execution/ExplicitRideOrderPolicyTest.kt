package com.zhangti.utalk.agent.tool.execution

import com.zhangti.utalk.agent.tool.AgentTool
import com.zhangti.utalk.agent.tool.catalog.CatalogTool
import com.zhangti.utalk.agent.tool.catalog.ToolDomain
import com.zhangti.utalk.agent.tool.catalog.ToolMetadata
import com.zhangti.utalk.agent.tool.catalog.ToolRisk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitRideOrderPolicyTest {
    private val arguments = mapOf(
        "product_category" to "1",
        "estimate_trace_id" to "estimate-123",
    )

    @Test
    fun `explicit ride request may create one order without a second confirmation`() = runBlocking {
        var calls = 0
        val catalog = catalogWithOrderTool { calls++ }
        val invoker = CatalogToolInvoker(catalog)
        val turn = ToolTurnContext("从望京到国贸，直接打一个快车去")

        assertFalse(invoker.invoke(ORDER_TOOL, arguments, turn).isError == true)
        assertEquals(1, calls)
        assertTrue(invoker.invoke(ORDER_TOOL, arguments, turn).isError == true)
        assertEquals(1, calls)
    }

    @Test
    fun `price question and negation do not authorize ordering`() = runBlocking {
        var calls = 0
        val invoker = CatalogToolInvoker(catalogWithOrderTool { calls++ })
        for (text in listOf(
            "打车去国贸多少钱", "先不要叫车", "我只是想看看快车价格",
            "我说直接打车你会下单吗", "现在叫车？",
        )) {
            assertTrue(invoker.invoke(ORDER_TOOL, arguments, ToolTurnContext(text)).isError == true)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `prior price discussion followed by explicit order is authorized`() {
        assertTrue(RideOrderIntent.isExplicitOrderRequest("价格之前看过了，直接打一个快车去"))
        assertFalse(RideOrderIntent.isExplicitOrderRequest("直接打车多少钱"))
    }

    @Test
    fun `missing estimate parameters do not consume authorization`() = runBlocking {
        var calls = 0
        val invoker = CatalogToolInvoker(catalogWithOrderTool { calls++ })
        val turn = ToolTurnContext("直接下单快车")

        assertTrue(invoker.invoke(ORDER_TOOL, mapOf("product_category" to "1"), turn).isError == true)
        assertFalse(invoker.invoke(ORDER_TOOL, arguments, turn).isError == true)
        assertEquals(1, calls)
    }

    private fun catalogWithOrderTool(onCall: () -> Unit) = com.zhangti.utalk.agent.tool.catalog.ToolCatalog().apply {
        register(
            CatalogTool(
                ToolMetadata(
                    id = ORDER_TOOL,
                    provider = "滴滴出行",
                    domain = ToolDomain.RIDE,
                    summary = "直接创建订单",
                    risk = ToolRisk.EXPLICIT_RIDE_ORDER,
                ),
                object : AgentTool {
                    override val definition = Tool(
                        name = ORDER_TOOL,
                        description = "直接创建订单",
                        inputSchema = ToolSchema(properties = JsonObject(emptyMap())),
                    )

                    override suspend fun call(arguments: Map<String, Any?>): CallToolResult {
                        onCall()
                        return CallToolResult(content = emptyList(), isError = false)
                    }
                },
            )
        )
    }

    companion object {
        private const val ORDER_TOOL = "ride__taxi_create_order"
    }
}
