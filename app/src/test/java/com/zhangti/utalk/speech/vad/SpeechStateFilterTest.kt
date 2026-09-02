package com.zhangti.utalk.speech.vad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SpeechStateFilter 迟滞逻辑单元测试。
 */
class SpeechStateFilterTest {

    @Test
    fun `threshold 0 - speech activates immediately and ends on first silence`() {
        val filter = SpeechStateFilter(minSpeechFrames = 0, minSilenceFrames = 0)

        assertTrue(filter.update(true))
        assertTrue(filter.update(true))
        assertFalse(filter.update(false))
    }

    @Test
    fun `minSpeechFrames delays activation`() {
        val filter = SpeechStateFilter(minSpeechFrames = 2, minSilenceFrames = 0)

        assertFalse(filter.update(true))
        assertFalse(filter.update(true))
        assertTrue(filter.update(true)) // 第 3 帧语音才进入语音段
    }

    @Test
    fun `minSilenceFrames keeps speech during short pauses`() {
        val filter = SpeechStateFilter(minSpeechFrames = 0, minSilenceFrames = 2)

        assertTrue(filter.update(true))
        assertTrue(filter.update(false)) // 短暂停顿仍算语音
        assertTrue(filter.update(false))
        assertFalse(filter.update(false)) // 第 3 帧连续静音结束语音段
    }

    @Test
    fun `speech resumes after silence ends previous segment`() {
        val filter = SpeechStateFilter(minSpeechFrames = 0, minSilenceFrames = 0)

        assertTrue(filter.update(true))
        assertFalse(filter.update(false))
        assertTrue(filter.update(true)) // 新一段语音重新触发
    }
}
