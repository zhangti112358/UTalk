# UTalk 语音模块

语音能力按职责拆分，底层 ASR、TTS、VAD、录音与播放均可独立测试，`conversation/` 只负责组合它们。

- [`audio/`](audio/readme.md)：持续麦克风采集和前置音频缓冲。
- [`vad/`](vad/readme.md)：本地语音活动检测与连续状态输出。
- [`asr/`](asr/readme.md)：豆包流式语音识别。
- [`tts/`](tts/readme.md)：豆包流式语音合成。
- [`playback/`](playback/readme.md)：TTS PCM 播放和打断进度。
- [`conversation/`](conversation/readme.md)：完整语音对话流程编排。

