package com.zhangti.utalk.speech.audio

import java.util.ArrayDeque

/** 保存最近一小段 PCM，让语音开始被 VAD 确认后仍能带上开头几百毫秒。 */
class PcmRingBuffer(private val capacityBytes: Int) {
    private val chunks = ArrayDeque<ByteArray>()
    private var sizeBytes = 0

    init {
        require(capacityBytes > 0)
    }

    @Synchronized
    fun append(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        val copy = pcm.copyOf()
        chunks.addLast(copy)
        sizeBytes += copy.size
        while (sizeBytes > capacityBytes && chunks.isNotEmpty()) {
            sizeBytes -= chunks.removeFirst().size
        }
    }

    @Synchronized
    fun snapshot(): ByteArray {
        val output = ByteArray(sizeBytes)
        var offset = 0
        chunks.forEach {
            it.copyInto(output, offset)
            offset += it.size
        }
        return output
    }

    @Synchronized
    fun clear() {
        chunks.clear()
        sizeBytes = 0
    }
}

