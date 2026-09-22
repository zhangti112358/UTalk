# Agent 启动装配

负责把配置、远程 MCP 服务、工具目录和会话上下文组装成可运行的出行 Agent 环境。

## 文件

- `TravelToolEnvironment.kt`：并行连接高德、飞友、DIDA、滴滴和彩云 MCP，并给工具添加领域、风险、命名空间和核心/可搜索标记。它同时管理这些 MCP Provider 的生命周期并返回加载报告。

