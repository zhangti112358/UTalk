package com.zhangti.utalk.agent.llm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageMessageAdapterTest {
    @Test
    fun `encodes original bytes in standard image_url part`() {
        val file = File.createTempFile("image-message-", ".jpg")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val message = ImageMessageAdapter.userMessage(
                LlmMessage(LlmRole.USER, "观察这张照片", images = listOf(LlmImage(file.path)))
            )
            val parts = message.content().asArrayOfContentParts()
            assertEquals("观察这张照片", parts[0].asText().text())
            assertEquals("data:image/jpeg;base64,AQID", parts[1].asImageUrl().imageUrl().url())
        } finally { file.delete() }
    }

    @Test(expected = IllegalStateException::class)
    fun `missing original photo is an error instead of silent text fallback`() {
        ImageMessageAdapter.userMessage(LlmMessage(LlmRole.USER, images = listOf(LlmImage("/missing/photo.jpg"))))
    }
}
