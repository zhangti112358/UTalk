# Agent 界面

提供当前第一版纯文字 Agent 的 Android Compose 交互页面。

## 文件

- `TextAgentActivity.kt`：初始化同一个 Agent 会话，提供可切换的文字和持续语音模式，并显示识别文本、流式回答、工具调用和播报打断状态。
- `ActivityLocationPermissionGate.kt`：当 Agent 首次调用手机定位工具时显示 Android 定位授权弹窗，并把授权结果返回给工具线程。
