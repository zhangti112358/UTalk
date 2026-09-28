# 应用设置

- `SettingsCatalog.kt`：维护设置页 API key 顺序、批量 `key=value` 文本解析及滴滴环境枚举。
- `EncryptedSettingsStore.kt`：用 Android Keystore AES-GCM 加密保存密钥和常用信息，并持久化滴滴测试/正式环境选择。
- `SettingsRepository.kt`：统一读取手机保存值，密钥空缺时回退构建期 `secrets.properties`。
- `SettingsActivity.kt`：提供滴滴环境切换、API key 单项或批量填写，以及常用信息的编辑入口。

设置仅在重新打开 Agent、语音或模型页面后进入新会话。默认滴滴沙盒；正式环境可能产生真实订单。密钥不进入备份，且设置页禁止系统截图。
