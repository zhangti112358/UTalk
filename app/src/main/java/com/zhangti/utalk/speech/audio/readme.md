# 实时音频输入

把持续麦克风采集和前置音频缓冲从 VAD、ASR 与 Agent 中独立出来。

## 文件

- `PcmAudioSource.kt`：定义固定帧长 PCM 音频源接口与帧/错误回调。
- `MicrophonePcmSource.kt`：用通信场景采集 16kHz 单声道麦克风数据，启用并记录系统回声消除状态。
- `PcmRingBuffer.kt`：保存最近几百毫秒 PCM，在 VAD 确认语音开始后补回可能被检测延迟截掉的开头。
