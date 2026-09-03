# 豆包TTS

## Android sdk文档
https://docs.volcengine.com/docs/6561/2532486?lang=zh

（API 协议见官方「API参考 → 语音合成大模型 → 双向流式语音合成WebSocket」文档）

# 实现内容

1. ✅ DoubaoTTS 类：双向流式 WebSocket 协议（OkHttp 直连），输入文本 → 流式返回 PCM 音频
2. ✅ TtsTestActivity：文本合成播放测试页（MainActivity「测试 TTS」进入，播放中再点 = 打断重播）

# 对外接口

### `DoubaoTTS`（流式合成客户端）

```kotlin
DoubaoTTS(
    apiKey: String = AppConfig.instance.doubaoApiKey,
    speaker: String = TtsConstants.DEFAULT_SPEAKER,   // 音色
    sampleRate: Int = TtsConstants.SAMPLE_RATE,       // 16000
)

fun startStream(listener: Listener): Session   // 建连 + StartConnection + StartSession
```

### `DoubaoTTS.Listener`（回调，在 OkHttp WS 线程，UI 更新需自行切线程）

```kotlin
fun onAudio(pcm: ByteArray)          // 合成音频（PCM 16kHz/16bit/mono）
fun onCompleted()                    // 合成正常结束
fun onError(throwable: Throwable)    // 合成出错
```

### `DoubaoTTS.Session`（会话句柄）

```kotlin
fun sendText(text: String)   // 发送待合成文本（可多次，会话未就绪时自动暂存）
fun finish()                 // 文本发送完毕，触发服务端收尾
fun cancel()                 // 打断：发送 CancelSession 并断开
```

用法：

```kotlin
val session = DoubaoTTS().startStream(object : DoubaoTTS.Listener {
    override fun onAudio(pcm: ByteArray) { /* 写入 AudioTrack 播放 */ }
    override fun onCompleted() {}
    override fun onError(throwable: Throwable) {}
})
session.sendText("你好")
session.finish()
```

**打断**：`session.cancel()` 后立即 `startStream(...)` 开新会话播放新文本即可。

### `TtsConstants`（协议与配置常量，`TtsConstants.kt`）

- `TTS_URL`：wss 接口地址；`RESOURCE_ID`：`seed-tts-2.0`；`DEFAULT_SPEAKER`：默认音色；`SAMPLE_RATE`：16000
- 其余为二进制帧协议常量（消息类型 / 事件号 / 序列化 / 压缩）

注意：密钥需在 `secrets.properties` 配置 `doubao`（新版控制台 API Key，X-Api-Key 鉴权）。
