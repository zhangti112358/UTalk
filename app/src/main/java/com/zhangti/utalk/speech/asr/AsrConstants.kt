package com.zhangti.utalk.speech.asr

/**
 * 豆包流式语音识别（ASR）协议与配置常量。
 *
 * 二进制帧格式：4 字节 header（版本/header 长度、消息类型/标志、序列化/压缩、保留）
 * + 4 字节序号 + 4 字节 payload 长度 + payload，整数均为大端。
 */
object AsrConstants {

    /** WebSocket 地址（双向流式大模型识别） */
    const val ASR_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"

    /** 模型资源 ID（豆包流式识别 2.0 小时版） */
    const val RESOURCE_ID = "volc.seedasr.sauc.duration"

    /** 音频采样率（Hz）：16kHz / 16bit / 单声道裸 PCM */
    const val SAMPLE_RATE = 16000

    // ── 帧 header ──
    const val PROTOCOL_VERSION: Byte = 0b0001 // 协议版本 v1
    const val DEFAULT_HEADER_SIZE: Byte = 0b0001 // header 长度 = 1 × 4 字节

    // ── 消息类型 ──
    const val CLIENT_FULL_REQUEST: Byte = 0b0001 // 完整客户端请求（请求参数 JSON）
    const val CLIENT_AUDIO_ONLY_REQUEST: Byte = 0b0010 // 纯音频帧
    const val SERVER_FULL_RESPONSE: Byte = 0b1001 // 服务端正常响应
    const val SERVER_ERROR_RESPONSE: Byte = 0b1111 // 服务端错误响应

    // ── 类型标志 ──
    const val POS_SEQUENCE: Byte = 0b0001 // 带正序号
    const val NEG_WITH_SEQUENCE: Byte = 0b0011 // 带负序号（最后一帧，通知音频结束）

    // ── 序列化 / 压缩 ──
    const val NO_SERIALIZATION: Byte = 0b0000
    const val JSON: Byte = 0b0001
    const val GZIP: Byte = 0b0001
}
