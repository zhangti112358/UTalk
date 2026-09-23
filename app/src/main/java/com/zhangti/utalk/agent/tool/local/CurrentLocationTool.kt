package com.zhangti.utalk.agent.tool.local

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonObject

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timestampMillis: Long,
)

fun interface CurrentLocationSource {
    suspend fun read(): DeviceLocation
}

/** 按需读取一次手机位置；不会订阅后台位置更新。 */
class CurrentLocationTool(private val source: CurrentLocationSource) : AgentTool {
    override val definition = Tool(
        name = ID,
        description = "获取本机当前地理位置。返回经纬度，如需地址名称，再用高德逆地理编码。",
        inputSchema = ToolSchema(properties = JsonObject(emptyMap())),
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult =
        try {
            val fix = source.read()
            val coordinate = String.format(Locale.US, "%.6f,%.6f", fix.longitude, fix.latitude)
            CallToolResult(
                content = listOf(TextContent(
                    "手机当前位置坐标（经度,纬度）：$coordinate；" +
                        "精度约 ${fix.accuracyMeters.toInt()} 米；" +
                        "定位时间：${Instant.ofEpochMilli(fix.timestampMillis)}；" +
                        "来源：${fix.provider}。若需要地名，请调用高德地图逆地理编码。"
                )),
                isError = false,
            )
        } catch (error: Exception) {
            CallToolResult(
                content = listOf(TextContent(error.message ?: "无法获取手机当前位置")),
                isError = true,
            )
        }

    companion object {
        const val ID = "device_current_location"
    }
}
