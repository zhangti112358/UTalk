package com.zhangti.utalk.speech.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoTranscriptFilterTest {
    @Test
    fun `punctuation and a small recognition error still match played speech`() {
        val reply = "北京今天晴，最高 26 度。建议带一件薄外套。"
        assertTrue(EchoTranscriptFilter.isLikelyEcho("北京今天晴最高26度", reply))
        assertTrue(EchoTranscriptFilter.isLikelyEcho("北京今天情最高26度", reply))
        assertTrue(EchoTranscriptFilter.isLikelyEcho("建议带一件薄外套", reply))
    }

    @Test
    fun `different user request is not mistaken for echo`() {
        val reply = "北京今天晴，最高 26 度。建议带一件薄外套。"
        assertFalse(EchoTranscriptFilter.isLikelyEcho("帮我订明天去上海的机票", reply))
        assertFalse(EchoTranscriptFilter.isLikelyEcho("帮我", reply))
    }
}
