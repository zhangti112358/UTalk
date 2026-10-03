# 分析脚本

- `analyze-context-tokens.mjs`：只读分析 Android 历史数据库与工具 Schema，输出不含原始对话、密钥和位置的 Token 聚合统计。使用 Node.js 和 SQLite CLI；运行 `node scripts/analyze-context-tokens.mjs /path/to/database`，不会调用模型或创建订单。
