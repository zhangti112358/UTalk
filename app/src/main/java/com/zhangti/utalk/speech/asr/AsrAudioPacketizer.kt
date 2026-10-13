package com.zhangti.utalk.speech.asr

import java.io.ByteArrayOutputStream

/**
 * 实时音频按约 100 ms 聚合；已经积累的短前置音频直接发送，超过 1 秒才拆包。
 * 只处理 PCM 分包，不负责网络连接与会话生命周期。
 */
internal class AsrAudioPacketizer(
    private val send: (ByteArray) -> Unit,
    private val packetBytes: Int = BYTES_PER_100_MS,
    private val initialSplitThresholdBytes: Int = BYTES_PER_SECOND,
) {
    private val pending = ByteArrayOutputStream(packetBytes * 2)

    fun sendInitial(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        if (pcm.size <= initialSplitThresholdBytes) send(pcm.copyOf())
        else pcm.forEachChunk(packetBytes, send)
    }

    fun append(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        pending.write(pcm)
        val bytes = pending.toByteArray()
        var offset = 0
        while (bytes.size - offset >= packetBytes) {
            send(bytes.copyOfRange(offset, offset + packetBytes))
            offset += packetBytes
        }
        pending.reset()
        if (offset < bytes.size) pending.write(bytes, offset, bytes.size - offset)
    }

    fun flush() {
        val bytes = pending.toByteArray()
        pending.reset()
        if (bytes.isNotEmpty()) send(bytes)
    }

    private fun ByteArray.forEachChunk(size: Int, consumer: (ByteArray) -> Unit) {
        var offset = 0
        while (offset < this.size) {
            val end = minOf(offset + size, this.size)
            consumer(copyOfRange(offset, end))
            offset = end
        }
    }

    companion object {
        private const val BYTES_PER_SECOND = AsrConstants.SAMPLE_RATE * 2
        private const val BYTES_PER_100_MS = BYTES_PER_SECOND / 10
    }
}
