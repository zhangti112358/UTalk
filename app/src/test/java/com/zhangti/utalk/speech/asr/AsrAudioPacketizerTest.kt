package com.zhangti.utalk.speech.asr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AsrAudioPacketizerTest {
    @Test fun `live frames are aggregated into 100ms packets and remainder is flushed`() {
        val sent = mutableListOf<ByteArray>()
        val packetizer = AsrAudioPacketizer(sent::add)
        repeat(4) { packetizer.append(ByteArray(1_024) { it.toByte() }) }
        assertEquals(listOf(3_200), sent.map(ByteArray::size))
        packetizer.flush()
        assertEquals(listOf(3_200, 896), sent.map(ByteArray::size))
    }

    @Test fun `short initial buffer is sent once while over one second is split`() {
        val sent = mutableListOf<ByteArray>()
        val packetizer = AsrAudioPacketizer(sent::add)
        val short = ByteArray(16_000) { 7 }
        packetizer.sendInitial(short)
        assertEquals(1, sent.size)
        assertArrayEquals(short, sent.single())

        sent.clear()
        packetizer.sendInitial(ByteArray(32_001))
        assertEquals(listOf(3_200, 3_200, 3_200, 3_200, 3_200, 3_200, 3_200, 3_200, 3_200, 3_200, 1), sent.map(ByteArray::size))
    }
}
