package com.zhangti.utalk.speech.vad

import android.content.Context
import java.io.Closeable

data class SpeechActivity(
    val isSpeech: Boolean,
    val changed: Boolean,
)

/** 只负责把连续 PCM 帧转换成“当前是否在说话”的稳定状态。 */
interface ContinuousVadProcessor : Closeable {
    fun process(frame: ByteArray): SpeechActivity
}

class SileroContinuousVadProcessor(
    context: Context,
    speechDurationMs: Int = 96,
    silenceDurationMs: Int = 640,
) : ContinuousVadProcessor {
    private val vad = SileroVad(
        context = context,
        sampleRate = VadSampleRate.SAMPLE_RATE_16K,
        frameSize = VadFrameSize.FRAME_SIZE_512,
        mode = VadMode.NORMAL,
        speechDurationMs = speechDurationMs,
        silenceDurationMs = silenceDurationMs,
    )
    private var previous = false

    override fun process(frame: ByteArray): SpeechActivity {
        val current = vad.isSpeech(frame)
        val result = SpeechActivity(current, current != previous)
        previous = current
        return result
    }

    override fun close() = vad.close()
}

