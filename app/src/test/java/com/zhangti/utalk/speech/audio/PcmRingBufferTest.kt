package com.zhangti.utalk.speech.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PcmRingBufferTest {
    @Test
    fun `keeps newest complete chunks within capacity`() {
        val buffer = PcmRingBuffer(capacityBytes = 6)
        buffer.append(byteArrayOf(1, 2, 3, 4))
        buffer.append(byteArrayOf(5, 6, 7, 8))

        assertArrayEquals(byteArrayOf(5, 6, 7, 8), buffer.snapshot())
    }

    @Test
    fun `snapshot is an independent copy`() {
        val buffer = PcmRingBuffer(capacityBytes = 8)
        val input = byteArrayOf(1, 2, 3)
        buffer.append(input)
        input[0] = 9

        assertArrayEquals(byteArrayOf(1, 2, 3), buffer.snapshot())
    }
}
