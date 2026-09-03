package com.zhangti.utalk.speech.asr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangti.utalk.AppConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DoubaoAsr 设备端测试：用 hello.wav（16kHz 真人语音）走真实接口验证流式识别。
 * 需要 secrets.properties 里配置了 doubao key。
 */
@RunWith(AndroidJUnit4::class)
class DoubaoAsrTest {

    @Test
    fun startStream_onHelloWav_returnsText() {
        assumeTrue("未配置 doubao API Key，跳过真实接口测试", AppConfig.instance.doubaoApiKey.isNotBlank())

        val done = CountDownLatch(1) // 完成或失败，二选一
        val partials = mutableListOf<String>()
        val finals = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val asr = DoubaoAsr()
        val session = asr.startStream(object : DoubaoAsr.Listener {
            override fun onResult(text: String, isFinal: Boolean) {
                partials.add(text)
                if (isFinal) finals.add(text)
            }

            override fun onError(throwable: Throwable) {
                errors.add(throwable.message ?: "未知错误")
                done.countDown()
            }

            override fun onCompleted() {
                done.countDown()
            }
        })

        // 流式发送 hello.wav（16kHz 真人语音），按 100ms 一包（连接未就绪时自动暂存）
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val audio = testContext.assets.open("hello.wav").use { input ->
            input.skip(44)
            input.readBytes()
        }
        val chunkBytes = AsrConstants.SAMPLE_RATE * 2 * 100 / 1000
        var offset = 0
        while (offset < audio.size) {
            val end = minOf(offset + chunkBytes, audio.size)
            session.sendPcm(audio.copyOfRange(offset, end))
            offset = end
            Thread.sleep(20) // 轻量限速，接近实时流
        }
        session.finish()

        assertTrue("等待识别结束超时", done.await(60, TimeUnit.SECONDS))
        if (errors.isNotEmpty()) {
            fail("识别出错：${errors.firstOrNull()}")
        }
        assertTrue("应收到实时部分结果", partials.isNotEmpty())
        assertTrue("最终结果不应为空", finals.isNotEmpty())
    }
}
