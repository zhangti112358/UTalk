# 应用入口

- `AppConfig.kt`：从构建配置中统一读取豆包、DeepSeek 和地图等服务的参数。
- `LocalConfig.kt`：保留本地配置的类型与默认值，避免业务代码直接依赖密钥来源。
- `MainActivity.kt`：应用主入口，提供各测试页和 Agent 页面的导航。
- `agent/`：Agent 上下文、循环、工具和界面。
- `speech/`：录音、VAD、ASR、TTS 与语音对话编排。
- `ui/`：预留的公共 UI 组件目录。
