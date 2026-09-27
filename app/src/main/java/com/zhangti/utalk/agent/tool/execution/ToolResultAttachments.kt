package com.zhangti.utalk.agent.tool.execution

import com.zhangti.utalk.agent.context.ImageContext
import com.zhangti.utalk.agent.tool.local.TakePhotoTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.jsonPrimitive

/** 只有受信任的本地拍照工具能提供私有图片引用，不接受远端提供的文件路径。 */
object ToolResultAttachments {
    fun images(toolName: String, result: CallToolResult): List<ImageContext> {
        if (toolName != TakePhotoTool.ID || result.isError == true) return emptyList()
        val data = result.structuredContent ?: return emptyList()
        val path = data["photo_path"]?.jsonPrimitive?.content ?: return emptyList()
        val at = data["captured_at"]?.jsonPrimitive?.content ?: return emptyList()
        return listOf(ImageContext(path, at))
    }
}
