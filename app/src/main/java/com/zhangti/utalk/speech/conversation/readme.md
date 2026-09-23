# 语音会话编排

只负责组合音频输入、VAD、ASR、文字 Agent 和 TTS，各底层模块仍可独立测试和替换。

## 文件

- `VoiceConversationController.kt`：持续监听 VAD 状态，携带 500ms 前置音频启动 ASR，将最终文本送入 Agent，再把模型增量交给 TTS；播报时用 ASR 结果确认插话，之后停止播报并记录估算的已听前缀。
- `EchoTranscriptFilter.kt`：识别播报期间 ASR 返回的当前回复片段，避免把设备自己的声音当作用户插话。
