# 出行 MCP 工具索引

每份文件记录一个远程服务通过 `tools/list` 返回的工具名称、描述和输入 Schema。它们是接口快照；服务升级后可运行 `McpToolListDumpTest` 重新导出并检查差异。文档中的地址已隐去密钥。

| 服务 | 工具数 | 接口文档 |
| --- | ---: | --- |
| 高德地图 | 15 | [amap-maps.md](amap-maps.md) |
| 飞友航班 | 9 | [VariFlight-Aviation.md](VariFlight-Aviation.md) |
| DIDA 酒店 | 3 | [DIDA-Hotel.md](DIDA-Hotel.md) |
| 滴滴出行 | 13 | [DiDi-Ride.md](DiDi-Ride.md) |
| 彩云天气 | 5 | [Caiyun-Weather.md](Caiyun-Weather.md) |

滴滴远端接口有两条不同的叫车路径：`taxi_generate_ride_app_link` 只生成跳转链接；`taxi_create_order` 则直接调用 API 创建订单。完整参数和约束见 [滴滴 MCP 接入文档](../didi/mcp.md)。

当前 App 使用滴滴沙盒端点，返回模拟订单，不会产生真实叫车。Agent 内的工具名带 `ride__` 前缀，例如 `ride__taxi_create_order`；这是应用层命名空间，发往滴滴时仍使用原始工具名。滴滴的地图和路线工具、滴滴和高德的打车跳转链接工具均不向模型暴露；地点与路线由高德负责。用户本轮明确要求叫车时，Agent 可以预估后直接调用一次创建订单接口；取消订单仍不可执行。
