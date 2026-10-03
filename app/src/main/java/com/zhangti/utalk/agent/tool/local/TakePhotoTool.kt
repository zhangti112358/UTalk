package com.zhangti.utalk.agent.tool.local

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class CapturedPhoto(
    val path: String, val capturedAt: String, val lens: String,
    val galleryUri: String? = null, val galleryError: String? = null,
)

fun interface PhotoCaptureSource {
    suspend fun capture(): CapturedPhoto
}

/** 只获取画面，不单独调用视觉模型；原图交给主 Agent 上下文。 */
class TakePhotoTool(private val source: PhotoCaptureSource) : AgentTool {
    override val definition = Tool(
        name = ID,
        description = "拍摄手机前方画面，用于观察当前环境或物品。",
        inputSchema = ToolSchema(properties = JsonObject(emptyMap())),
    )

    override suspend fun call(arguments: Map<String, Any?>): CallToolResult = try {
        val photo = source.capture()
        CallToolResult(
            content = listOf(TextContent("拍摄成功，时间：${photo.capturedAt}；镜头：${photo.lens}。原图将在下一次模型请求中提供。" +
                when {
                    photo.galleryUri != null -> "照片已保存到系统相册。"
                    photo.galleryError != null -> "原图已保留，但保存相册失败：${photo.galleryError}。"
                    else -> ""
                })),
            structuredContent = JsonObject(mapOf(
                "photo_path" to JsonPrimitive(photo.path),
                "captured_at" to JsonPrimitive(photo.capturedAt),
            )),
            isError = false,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        CallToolResult(listOf(TextContent(error.message ?: "拍照失败，请重试")), isError = true)
    }

    companion object { const val ID = "device_take_photo" }
}
