package com.zhangti.utalk.agent.llm

import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import java.io.File
import java.util.Base64

/** 只在 API 边界读取原图并编码，避免 Base64 混入文字上下文。 */
object ImageMessageAdapter {
    fun userMessage(message: LlmMessage): ChatCompletionUserMessageParam {
        require(message.role == LlmRole.USER) { "图片只能放在 user 消息中" }
        val parts = mutableListOf<ChatCompletionContentPart>()
        message.content?.takeIf { it.isNotBlank() }?.let {
            parts += ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder().text(it).build())
        }
        message.images.forEach { image ->
            val file = File(image.path)
            check(file.isFile && file.length() in 1..32L * 1024 * 1024) { "图片文件不存在、为空或超过 32 MiB，请重新拍摄" }
            val url = "data:${image.mimeType};base64," + Base64.getEncoder().encodeToString(file.readBytes())
            parts += ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder().imageUrl(
                    ChatCompletionContentPartImage.ImageUrl.builder().url(url).build()
                ).build()
            )
        }
        return ChatCompletionUserMessageParam.builder().contentOfArrayOfContentParts(parts).build()
    }
}
