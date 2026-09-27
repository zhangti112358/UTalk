# Agent 界面

- `PhotoCaptureCoordinator.kt`：桥接工具与前台相机，负责权限、最广后置镜头选择、自动对焦拍摄和会话照片清理。
- `PhotoCapturePanel.kt`：显示自动拍照的小型预览窗口和最近照片缩略图，提供取消入口。

提供当前第一版纯文字 Agent 的 Android Compose 交互页面。

## 文件

- `TextAgentActivity.kt`：初始化同一个 Agent 会话，提供可切换的文字和持续语音模式，并显示识别文本、流式回答、工具调用和播报打断状态。
- `ActivityLocationPermissionGate.kt`：当 Agent 首次调用手机定位工具时显示 Android 定位授权弹窗，并把授权结果返回给工具线程。
