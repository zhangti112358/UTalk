package com.zhangti.utalk.speech.vad

import java.io.Closeable

/**
 * 语音活动检测（VAD）统一接口。
 *
 * 调用方以固定帧长（见各实现）持续喂入音频帧，获得「该帧是否属于语音段」的结果。
 * 实现内部自行维护跨帧上下文状态。
 */
interface VoiceActivityDetector : Closeable {

    /** 分析一帧 Float 音频（已归一化到 [-1, 1]）。 */
    fun isSpeech(audio: FloatArray): Boolean

    /** 分析一帧 16-bit PCM 音频。 */
    fun isSpeech(audio: ShortArray): Boolean

    /** 分析一帧 16-bit little-endian PCM 音频（长度应为 2 × 帧长）。 */
    fun isSpeech(audio: ByteArray): Boolean
}
