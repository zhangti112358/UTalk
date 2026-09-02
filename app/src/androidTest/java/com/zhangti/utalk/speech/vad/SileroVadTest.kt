package com.zhangti.utalk.speech.vad

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/**
 * Silero VAD 设备端测试：加载真实模型（silero_vad.onnx）验证推理结果。
 *
 * hello.wav（16kHz 真人语音「Hello」）取自 gkonovalov/android-vad 的测试资产，
 * 期望结果序列与其 SileroVadTest 保持一致（VERY_AGGRESSIVE / 16kHz / 512 帧长）。
 */
@RunWith(AndroidJUnit4::class)
class SileroVadTest {

    /** 应用上下文：读取 app assets 里的模型。 */
    private lateinit var context: Context

    /** 测试上下文：读取 androidTest assets 里的 hello.wav。 */
    private lateinit var testContext: Context

    private lateinit var vad: SileroVad

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testContext = InstrumentationRegistry.getInstrumentation().context

        vad = SileroVad(
            context = context,
            sampleRate = VadSampleRate.SAMPLE_RATE_16K,
            frameSize = VadFrameSize.FRAME_SIZE_512,
            mode = VadMode.VERY_AGGRESSIVE,
        )
    }

    @After
    fun tearDown() {
        vad.close()
    }

    @Test
    fun isSpeech_onHelloWav_detectsSpeechSegment() {
        val actual = mutableListOf<Boolean>()
        val expected = listOf(
            false, false, false, false, false,
            false, true, true, true, true,
            true, true, true, true, true,
            true, true, true, false, false
        )

        // ByteArray 一帧 = 2 × 帧长
        val chunkSize = vad.frameSize.value * 2
        testContext.assets.open("hello.wav").buffered().use { input ->
            input.skip(44) // 跳过 WAV 头
            while (input.available() > 0) {
                val frame = ByteArray(chunkSize).apply { input.read(this) }
                actual.add(vad.isSpeech(frame))
            }
        }

        assertEquals(expected, actual)
    }

    @Test
    fun isSpeech_onSilence_returnsFalse() {
        val silence = ByteArray(vad.frameSize.value * 2)

        repeat(10) { assertFalse(vad.isSpeech(silence)) }
    }

    @Test
    fun isSpeech_onPureTone_returnsFalse() {
        // 440Hz 纯正弦波不是语音，VERY_AGGRESSIVE 下不应触发
        val tone = ShortArray(vad.frameSize.value) { i ->
            (3000 * sin(2 * PI * 440 * i / 16000.0)).toInt().toShort()
        }

        repeat(5) { assertFalse(vad.isSpeech(tone)) }
    }

    @Test
    fun isSpeech_normalMode_detectsSpeech() {
        val normal = SileroVad(
            context = context,
            sampleRate = VadSampleRate.SAMPLE_RATE_16K,
            frameSize = VadFrameSize.FRAME_SIZE_512,
            mode = VadMode.NORMAL,
        )
        try {
            val results = mutableListOf<Boolean>()
            val chunkSize = VadFrameSize.FRAME_SIZE_512.value * 2
            testContext.assets.open("hello.wav").buffered().use { input ->
                input.skip(44)
                while (input.available() > 0) {
                    val frame = ByteArray(chunkSize).apply { input.read(this) }
                    results.add(normal.isSpeech(frame))
                }
            }

            assertTrue("NORMAL 模式下应检测到语音", results.any { it })
        } finally {
            normal.close()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFrameSize_throws() {
        // 8kHz 不支持 1536 帧长
        SileroVad(
            context = context,
            sampleRate = VadSampleRate.SAMPLE_RATE_8K,
            frameSize = VadFrameSize.FRAME_SIZE_1536,
        )
    }

    @Test
    fun isSpeech_afterClose_throws() {
        val local = SileroVad(
            context = context,
            sampleRate = VadSampleRate.SAMPLE_RATE_16K,
            frameSize = VadFrameSize.FRAME_SIZE_512,
            mode = VadMode.NORMAL,
        )
        local.close()

        try {
            local.isSpeech(ByteArray(VadFrameSize.FRAME_SIZE_512.value * 2))
            fail("关闭后调用 isSpeech 应抛出异常")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
