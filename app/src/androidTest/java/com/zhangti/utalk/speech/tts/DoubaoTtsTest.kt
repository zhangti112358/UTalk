package com.zhangti.utalk.speech.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangti.utalk.AppConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DoubaoTTS 设备端测试：走真实接口合成「你好」，验证收到 PCM 音频并正常收尾。
 * 需要 secrets.properties 里配置了 doubao key。
 */
@RunWith(AndroidJUnit4::class)
class DoubaoTtsTest {

    @Test
    fun startStream_onHello_returnsAudio() {
        assumeTrue("未配置 doubao API Key，跳过真实接口测试", AppConfig.instance.doubaoApiKey.isNotBlank())

        val done = CountDownLatch(1) // 完成或失败，二选一
        val audioChunks = mutableListOf<ByteArray>()
        val errors = mutableListOf<String>()

        val tts = DoubaoTTS()
        val session = tts.startStream(object : DoubaoTTS.Listener {
            override fun onAudio(pcm: ByteArray) {
                audioChunks.add(pcm)
            }

            override fun onCompleted() {
                done.countDown()
            }

            override fun onError(throwable: Throwable) {
                errors.add(throwable.message ?: "未知错误")
                done.countDown()
            }
        })

        session.sendText("你好，世界")
        session.finish()

        assertTrue("等待合成结束超时", done.await(60, TimeUnit.SECONDS))
        if (errors.isNotEmpty()) {
            fail("合成出错：${errors.firstOrNull()}")
        }
        assertTrue("应收到合成音频", audioChunks.isNotEmpty())
        val totalBytes = audioChunks.sumOf { it.size }
        assertTrue("合成音频长度异常（$totalBytes 字节）", totalBytes > 1000)
    }
}
