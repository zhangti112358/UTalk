package com.zhangti.utalk.speech.vad

/**
 * 音频数据转换与帧数计算工具。
 * 参考 gkonovalov/android-vad 的 Silero 实现（MIT License）。
 */
internal object AudioUtils {

    /** 16-bit little-endian PCM ByteArray → 归一化 FloatArray（[-1, 1]） */
    fun toFloatArray(audio: ByteArray): FloatArray =
        FloatArray(audio.size / 2) { i ->
            ((audio[2 * i].toInt() and 0xFF) or (audio[2 * i + 1].toInt() shl 8)) / 32767.0f
        }

    /** 16-bit PCM ShortArray → 归一化 FloatArray（[-1, 1]） */
    fun toFloatArray(audio: ShortArray): FloatArray =
        FloatArray(audio.size) { i -> audio[i] / 32767.0f }

    /** 按采样率、帧长将时长（ms）换算为帧数。 */
    fun getFramesCount(sampleRate: Int, frameSize: Int, durationMs: Int): Int =
        durationMs / (frameSize / (sampleRate / 1000))
}
