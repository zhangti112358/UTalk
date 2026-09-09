package com.zhangti.utalk.agent.llm

import com.zhangti.utalk.AppConfig
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * DeepSeek 真实接口冒烟测试（需要 secrets.properties 配置 deepseek key）。
 * 验证流式输出与中断（close）。
 */
class DeepSeekLlmSmokeTest {

    @Test
    fun `stream returns text chunks`() {
        assumeTrue("未配置 deepseek API Key，跳过真实接口测试", AppConfig.instance.deepseekApiKey.isNotBlank())

        val llm = DeepSeekLlm()
        try {
            val stream = llm.stream(
                LlmRequest(
                    messages = listOf(
                        LlmMessage(LlmRole.SYSTEM, "你是优拓，一个简洁的语音助手。"),
                        LlmMessage(LlmRole.USER, "用一句话介绍你自己"),
                    )
                )
            )
            val text = StringBuilder()
            var finish: String? = null
            var chunks = 0
            while (true) {
                val chunk = stream.next() ?: break
                chunks++
                chunk.text?.let { text.append(it) }
                chunk.finishReason?.let { finish = it }
            }
            println("识别完成：${text}（${chunks} 块，finish=$finish）")
            assertTrue("应收到模型文本", text.isNotBlank())
        } finally {
            llm.close()
        }
    }

    @Test
    fun `close interrupts streaming`() {
        assumeTrue("未配置 deepseek API Key，跳过真实接口测试", AppConfig.instance.deepseekApiKey.isNotBlank())

        val llm = DeepSeekLlm()
        try {
            val stream = llm.stream(
                LlmRequest(
                    messages = listOf(
                        LlmMessage(LlmRole.USER, "写一篇一千字的文章，主题是人工智能的未来")
                    ),
                    maxTokens = 2000,
                )
            )
            // 取一两块后立即中断
            stream.next()
            stream.next()
            stream.close()
            assertTrue("close 后 next 应立即返回 null", stream.next() == null)
            println("中断验证通过")
        } finally {
            llm.close()
        }
    }
}
