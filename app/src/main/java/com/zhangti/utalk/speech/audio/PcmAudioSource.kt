package com.zhangti.utalk.speech.audio

import java.io.Closeable

/** 持续输出固定帧长 16kHz/16bit/mono PCM 的音频源。 */
interface PcmAudioSource : Closeable {
    val isRunning: Boolean
    /** 当前采集会话是否确认启用了系统 AEC；未知时按未启用处理。 */
    val isEchoCancellationActive: Boolean get() = false
    fun start(listener: Listener)
    fun stop()

    interface Listener {
        fun onFrame(pcm: ByteArray)
        fun onError(throwable: Throwable) {}
    }
}
