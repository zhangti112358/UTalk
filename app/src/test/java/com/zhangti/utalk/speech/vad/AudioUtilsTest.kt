package com.zhangti.utalk.speech.vad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AudioUtils 纯逻辑单元测试（参考 gkonovalov/android-vad 的 AudioUtilsTest）。
 */
class AudioUtilsTest {

    @Test
    fun `convert ShortArray to FloatArray`() {
        val input = shortArrayOf(100, 200, 300)
        val expected = floatArrayOf(0.003051851f, 0.006103702f, 0.009155553f)

        assertArrayEquals(expected, AudioUtils.toFloatArray(input), 0f)
    }

    @Test
    fun `convert ByteArray to FloatArray`() {
        val input = byteArrayOf(10, 20, 30)
        val expected = floatArrayOf(0.15655996f)

        assertArrayEquals(expected, AudioUtils.toFloatArray(input), 0f)
    }

    @Test
    fun `getFramesCount calculates frame count correctly`() {
        val sampleRate = 8000
        val frameSize = 256
        val durationMs = 300
        val expected = 9

        assertEquals(expected, AudioUtils.getFramesCount(sampleRate, frameSize, durationMs))
    }
}
