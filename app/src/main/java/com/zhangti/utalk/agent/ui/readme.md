# Agent 界面

- `PhotoCaptureCoordinator.kt`：桥接工具与前台相机，负责权限、最广后置镜头选择和自动对焦拍摄；原图保留在应用私有目录以支持完整历史。
- `PhotoGallerySaver.kt`：通过 MediaStore 将原图复制到系统相册，Android 10 及以上保存到 Pictures/UTalk，失败时清理未完成的相册条目。
- `PhotoCapturePanel.kt`：显示自动拍照的小型预览窗口和最近照片缩略图，提供取消入口。

提供当前第一版纯文字 Agent 的 Android Compose 交互页面。

## 文件

- `TextAgentActivity.kt`：应用启动入口并初始化 Agent 会话；顶部进入设置，底部并列提供极简语音开关、单行紧凑且随换行增高的输入框和上箭头发送按钮，工具调用事件继续显示在对话区。
- `ActivityLocationPermissionGate.kt`：当 Agent 首次调用手机定位工具时显示 Android 定位授权弹窗，并把授权结果返回给工具线程。
