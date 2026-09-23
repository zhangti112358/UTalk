# UTalk Agent 框架

第一版使用单主 Agent 的工具调用循环，同时支持文字输入和基于 VAD 的持续语音对话。

## 模块边界

- [`context/`](context/readme.md)：统一上下文元素、会话存储、请求组装和压缩扩展点。
- [`runtime/`](runtime/readme.md)：模型 → 工具 → 模型循环、流式事件、取消和每次模型调用入口。
- [`llm/`](llm/readme.md)：模型无关接口、请求类型和 DeepSeek 实现。
- [`tool/`](tool/readme.md)：本地与远程工具共用的基础抽象。
- [`tool/catalog/`](tool/catalog/readme.md)：完整工具目录、领域/风险元数据和检索。
- [`tool/discovery/`](tool/discovery/readme.md)：`search_tools`，搜索后把完整工具 Schema 插入当前上下文。
- [`tool/execution/`](tool/execution/readme.md)：参数解析、策略检查、调用及结果格式化。
- [`tool/model/`](tool/model/readme.md)：MCP 工具到模型工具的适配和远程工具命名空间。
- [`tool/mcp/`](tool/mcp/readme.md)：远程 MCP 连接、调用和限流。
- [`bootstrap/`](bootstrap/readme.md)：地图、航班、酒店、打车、天气 MCP 的独立加载与分类。
- [`ui/`](ui/readme.md)：文字 Agent 页面，只消费结构化 `AgentEvent`。
- [`../speech/audio/`](../speech/audio/readme.md)：持续麦克风采集与前置 PCM 缓冲。
- [`../speech/vad/`](../speech/vad/readme.md)：本地 Silero VAD 和连续说话状态。
- [`../speech/conversation/`](../speech/conversation/readme.md)：ASR、Agent、TTS 与播报打断编排。
- [`../speech/playback/`](../speech/playback/readme.md)：流式 TTS 播放和已播报进度估算。

## 工具暴露策略

高德常用地图工具和 `search_tools` 始终提供给模型。航班、酒店、天气、打车及低频地图工具保存在目录中；模型调用 `search_tools` 后，命中的完整定义会作为 `ToolAvailabilityContext` 加入会话，从下一次模型调用开始可用。

远程工具对模型使用稳定命名空间，例如：

```text
amap__maps_geo
flight__searchFlightsByDepArr
hotel__searchHotels
ride__taxi_estimate
weather__get_hourly_forecast
```

地点搜索与路线规划统一使用高德。滴滴的地图工具及生成打车链接的工具不进入 Agent 目录，只保留非链接网约车接口。用户本轮明确要求叫车时，Agent 可预估后通过滴滴接口尝试创建一次订单，不再二次确认；普通询价不会授权下单。取消订单仍被策略层拒绝。当前连接滴滴沙盒，创建结果为模拟订单。

## 压缩扩展

`ContextPipeline` 当前没有默认 Transformer。后续对话摘要、工具结果压缩、工具定义卸载都应增加独立 `ContextTransformer`，不修改 `AgentLoop` 或 `DeepSeekLlm`。
