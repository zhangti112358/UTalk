# VAD

基于 Silero VAD 模型的语音活动检测（参考 gkonovalov/android-vad）。

## 对外接口

### `VoiceActivityDetector`（接口）

```kotlin
fun isSpeech(audio: FloatArray): Boolean  // 一帧 Float 音频（已归一化 [-1, 1]）
fun isSpeech(audio: ShortArray): Boolean  // 一帧 16-bit PCM
fun isSpeech(audio: ByteArray): Boolean   // 一帧 16-bit LE PCM（长度 = 2 × 帧长）
fun close()                              // 释放资源，之后不可再用
```

### `SileroVad`（实现类）

```kotlin
SileroVad(
    context: Context,
    sampleRate: VadSampleRate = VadSampleRate.SAMPLE_RATE_16K, // 8K / 16K
    frameSize: VadFrameSize = VadFrameSize.FRAME_SIZE_512,     // 8K: 256/512/768；16K: 512/1024/1536
    mode: VadMode = VadMode.NORMAL,                            // OFF / NORMAL / AGGRESSIVE / VERY_AGGRESSIVE
    speechDurationMs: Int = 0,   // 语音段最短时长（ms），0 = 不限制
    silenceDurationMs: Int = 0,  // 静音段最短时长（ms），0 = 不限制
)
```

用法：按固定帧长持续喂入音频帧，返回该帧是否属于语音段（内部维护跨帧状态）。

```kotlin
val vad = SileroVad(context, mode = VadMode.AGGRESSIVE)
try {
    while (recording) {
        val isSpeech = vad.isSpeech(frameBytes)
    }
} finally {
    vad.close()
}
```

- 模型文件：`assets/silero_vad.onnx`（Silero Team, MIT License）
- 参数不合法抛 `IllegalArgumentException`；`close()` 后调用同样抛出
