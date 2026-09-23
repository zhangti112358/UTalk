package com.zhangti.utalk.agent.tool.catalog

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelToolExposurePolicyTest {
    @Test
    fun `DiDi maps and ride links are hidden while ordering remains visible`() {
        assertFalse(TravelToolExposurePolicy.isVisible("DiDi-Ride", "taxi_generate_ride_app_link"))
        for (name in listOf(
            "maps_direction_bicycling", "maps_direction_driving", "maps_direction_transit",
            "maps_direction_walking", "maps_place_around", "maps_regeocode", "maps_textsearch",
        )) {
            assertFalse(TravelToolExposurePolicy.isVisible("DiDi-Ride", name))
        }
        assertFalse(TravelToolExposurePolicy.isVisible("DiDi-Ride", "maps_future_route"))
        assertFalse(TravelToolExposurePolicy.isVisible("amap-maps", "maps_schema_take_taxi"))
        assertTrue(TravelToolExposurePolicy.isVisible("DiDi-Ride", "taxi_create_order"))
        assertTrue(TravelToolExposurePolicy.isVisible("DiDi-Ride", "taxi_estimate"))
        assertTrue(TravelToolExposurePolicy.isVisible("DiDi-Ride", "taxi_query_order"))
        assertTrue(TravelToolExposurePolicy.isVisible("amap-maps", "maps_direction_driving"))
    }

    @Test
    fun `ride search returns estimate and create ahead of provider map tools`() {
        val catalog = ToolCatalog()
        val names = listOf(
            "maps_direction_bicycling", "maps_direction_driving", "maps_direction_transit",
            "maps_direction_walking", "maps_place_around", "maps_regeocode", "maps_textsearch",
            "taxi_cancel_order", "taxi_create_order", "taxi_estimate", "taxi_get_driver_location",
            "taxi_query_order",
        )
        names.forEach { name ->
            val id = "ride__$name"
            catalog.register(
                CatalogTool(
                    ToolMetadata(id, "滴滴出行", ToolDomain.RIDE, "滴滴 $name"),
                    object : AgentTool {
                        override val definition = Tool(id, inputSchema = ToolSchema(properties = JsonObject(emptyMap())))
                        override suspend fun call(arguments: Map<String, Any?>) = CallToolResult(emptyList())
                    },
                )
            )
        }

        val matches = catalog.search("直接打一个快车去", ToolDomain.RIDE, limit = 5).map { it.metadata.id }
        assertTrue("ride__taxi_estimate" in matches)
        assertTrue("ride__taxi_create_order" in matches)
        assertFalse(matches.any { it.startsWith("ride__maps_") })
    }
}
