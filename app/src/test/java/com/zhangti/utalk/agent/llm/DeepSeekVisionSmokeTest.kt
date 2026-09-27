package com.zhangti.utalk.agent.llm

import com.zhangti.utalk.AppConfig
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 手动联网验证：UTALK_VISION_SMOKE=1 ./gradlew :app:testDebugUnitTest --tests '*DeepSeekVisionSmokeTest' */
class DeepSeekVisionSmokeTest {
    @Test
    fun `model reads original image again on followup`() {
        assumeTrue(System.getenv("UTALK_VISION_SMOKE") == "1" && AppConfig.instance.deepseekApiKey.isNotBlank())
        val file = File.createTempFile("utalk-vision-", ".jpg")
        val image = BufferedImage(720, 280, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, 720, 280)
        graphics.color = Color.BLACK
        graphics.font = Font("SansSerif", Font.BOLD, 90)
        graphics.drawString("UTALK 735", 50, 170)
        graphics.dispose()
        ImageIO.write(image, "jpg", file)
        val llm = DeepSeekLlm()
        try {
            val original = LlmMessage(LlmRole.USER, "只回答图片中的文字。", images = listOf(LlmImage(file.path)))
            val first = read(llm, listOf(original))
            assertTrue("视觉输入未识别数字", first.contains("735"))
            // 故意不把第一轮读出的文字放入回答，验证第二轮仍能直接读原图。
            val second = read(llm, listOf(original,
                LlmMessage(LlmRole.ASSISTANT, "我已经看过图片。"),
                LlmMessage(LlmRole.USER, "再看原图，只回答图上的三位数字。")))
            assertTrue("追问未识别原图", second.contains("735"))
        } finally { llm.close(); file.delete() }
    }

    private fun read(llm: DeepSeekLlm, messages: List<LlmMessage>): String {
        val stream = llm.stream(LlmRequest(messages, maxTokens = 150))
        return try {
            buildString { while (true) append((stream.next() ?: break).text.orEmpty()) }
        } finally { stream.close() }
    }
}
