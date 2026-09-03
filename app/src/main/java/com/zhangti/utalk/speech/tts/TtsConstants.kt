package com.zhangti.utalk.speech.tts

/**
 * 豆包双向流式语音合成（TTS）协议与配置常量。
 *
 * 二进制帧格式同 ASR：4 字节 header（版本/header 长度、消息类型/标志、序列化/压缩、保留）
 * + 事件号 + [sessionId 长度 + sessionId] + payload 长度 + payload，整数均为大端。
 */
object TtsConstants {

    /** WebSocket 地址（双向流式语音合成） */
    const val TTS_URL = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"

    /** 模型资源 ID（豆包语音合成大模型 2.0） */
    const val RESOURCE_ID = "seed-tts-2.0"

    /** 默认音色 */
    const val DEFAULT_SPEAKER = "zh_female_vv_uranus_bigtts"

    /** 输出音频采样率（Hz）：pcm / 16kHz / 单声道 / 16bit */
    const val SAMPLE_RATE = 16000

    // ── 帧 header ──
    const val PROTOCOL_VERSION: Byte = 0b0001 // 协议版本 v1
    const val DEFAULT_HEADER_SIZE: Byte = 0b0001 // header 长度 = 1 × 4 字节

    // ── 消息类型 ──
    const val MSG_FULL_CLIENT_REQUEST: Byte = 0b0001 // 客户端请求帧（带事件号）
    const val MSG_FULL_SERVER_RESPONSE: Byte = 0b1001 // 服务端事件帧
    const val MSG_AUDIO_ONLY_SERVER: Byte = 0b1011 // 服务端音频帧（Raw PCM）
    const val MSG_ERROR: Byte = 0b1111 // 服务端错误帧

    // ── 类型标志 ──
    const val FLAG_WITH_EVENT: Byte = 0b0100 // 携带事件号

    // ── 序列化 / 压缩 ──
    const val JSON: Byte = 0b0001
    const val NO_COMPRESSION: Byte = 0b0000
    const val GZIP: Byte = 0b0001

    // ── 事件号 ──
    const val EVENT_START_CONNECTION = 1 // 建立连接
    const val EVENT_FINISH_CONNECTION = 2 // 结束连接
    const val EVENT_CONNECTION_STARTED = 50 // 连接已建立
    const val EVENT_CONNECTION_FAILED = 51 // 连接失败
    const val EVENT_CONNECTION_FINISHED = 52 // 连接已结束
    const val EVENT_START_SESSION = 100 // 创建会话（携带音色/音频参数）
    const val EVENT_CANCEL_SESSION = 101 // 取消会话（打断）
    const val EVENT_FINISH_SESSION = 102 // 结束会话（文本发送完毕）
    const val EVENT_SESSION_STARTED = 150 // 会话已创建
    const val EVENT_SESSION_FINISHED = 152 // 会话合成结束
    const val EVENT_SESSION_FAILED = 153 // 会话失败
    const val EVENT_TASK_REQUEST = 200 // 发送待合成文本
}
