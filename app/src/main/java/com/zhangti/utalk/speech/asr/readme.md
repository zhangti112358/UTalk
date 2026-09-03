# 豆包 ASR

## api文档
https://docs.volcengine.com/docs/6561/2630027?lang=zh

## Android sdk 文档
https://docs.volcengine.com/docs/6561/2604754?lang=zh

## 协议实现参考（官方示例）
https://github.com/volcengine/sauc-python-demo

# 实现内容

1. ✅ DoubaoAsr 类：WebSocket 二进制协议（OkHttp 直连）
2. ✅ AsrTestActivity：麦克风实时流式识别测试页（MainActivity「测试 ASR」进入）

# 对外接口

### `DoubaoAsr`（流式识别客户端）

```kotlin
DoubaoAsr(apiKey: String = AppConfig.instance.doubaoApiKey)

fun startStream(listener: Listener): Session   // 建连并发 full request，返回会话
```

### `DoubaoAsr.Listener`（回调，在 OkHttp WS 线程，UI 更新需自行切主线程）

```kotlin
fun onResult(text: String, isFinal: Boolean)  // 累计识别文本；isFinal=最后一包
fun onError(throwable: Throwable)             // 识别出错
fun onCompleted()                             // 识别正常结束
```

### `DoubaoAsr.Session`（会话句柄）

```kotlin
fun sendPcm(pcm: ByteArray, size: Int = pcm.size)  // 送 16kHz/16bit/mono 裸 PCM
                                                   // （连接未就绪时自动暂存，就绪后按序补发）
fun finish(lastPcm: ByteArray? = null)             // 发送最后一包（负序号），等待收尾
fun cancel()                                       // 主动中断会话
```

用法：

```kotlin
val session = DoubaoAsr().startStream(object : DoubaoAsr.Listener {
    override fun onResult(text: String, isFinal: Boolean) { /* 显示 */ }
    override fun onError(throwable: Throwable) {}
    override fun onCompleted() {}
})
// 采集循环：session.sendPcm(chunk)
// 结束时：session.finish()
```

### `AsrConstants`（协议与配置常量，`AsrConstants.kt`）

- `ASR_URL`：wss 接口地址；`RESOURCE_ID`：模型版本（2.0 小时版）；`SAMPLE_RATE`：16000
- 其余为二进制帧协议常量（消息类型 / 序号标志 / 序列化 / 压缩）

注意：密钥需在 `secrets.properties` 配置 `doubao`（新版控制台 API Key，X-Api-Key 鉴权）。
