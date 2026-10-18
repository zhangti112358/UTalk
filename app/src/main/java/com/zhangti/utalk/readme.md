# 应用入口

- `AppConfig.kt`：统一读取手机设置或构建期密钥，并按所选环境构建滴滴测试/正式 MCP 地址。
- `LocalConfig.kt`：保留本地配置的类型与默认值，避免业务代码直接依赖密钥来源。
- `agent/ui/TextAgentActivity.kt`：应用启动入口，打开后直接进入 Agent 文字与持续语音对话。
- `settings/SettingsActivity.kt`：从 Agent 顶部进入设置，并在页面底部直接提供开发者工具入口。
- `UTalkApplication.kt`：启动时初始化加密设置仓库，让各服务在新会话中读取用户保存的配置。
- `settings/`：独立设置页、批量密钥解析与加密持久化，详情见 [`settings/readme.md`](settings/readme.md)。
- `agent/`：Agent 上下文、循环、工具和界面。
- `speech/`：录音、VAD、ASR、TTS 与语音对话编排。
- `ui/`：预留的公共 UI 组件目录。
