package com.zhangti.utalk.speech.vad

/**
 * VAD 支持的音频采样率（Hz）。Silero VAD 模型仅支持 8kHz 与 16kHz。
 */
enum class VadSampleRate(val value: Int) {
    SAMPLE_RATE_8K(8000),
    SAMPLE_RATE_16K(16000);
}

/**
 * 每帧音频采样点数。与采样率的合法组合：
 * 8kHz → 256 / 512 / 768；16kHz → 512 / 1024 / 1536。
 */
enum class VadFrameSize(val value: Int) {
    FRAME_SIZE_256(256),
    FRAME_SIZE_512(512),
    FRAME_SIZE_768(768),
    FRAME_SIZE_1024(1024),
    FRAME_SIZE_1536(1536);
}

/**
 * 检测模式：置信度阈值越激进，误检越少但漏检越多。
 */
enum class VadMode {
    /** 关闭检测（阈值 0，恒判为语音） */
    OFF,

    /** 阈值 0.5 */
    NORMAL,

    /** 阈值 0.8 */
    AGGRESSIVE,

    /** 阈值 0.95 */
    VERY_AGGRESSIVE;
}
