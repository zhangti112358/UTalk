# 本地设备工具

- `TakePhotoTool.kt`：将前台相机拍摄封装为工具，返回拍摄信息和原图引用，不单独分析图像。

- `CurrentLocationTool.kt`：定义一次性手机定位工具及可替换的数据源，向 Agent 返回经纬度、精度和时间。
- `AndroidCurrentLocationSource.kt`：按需申请权限，并短暂监听 Android GPS/网络定位；获取首个新位置或超时后立即停止。
- `CurrentTimeTool.kt`：读取手机当前本地时间，一次返回年月日、星期和时分秒。
