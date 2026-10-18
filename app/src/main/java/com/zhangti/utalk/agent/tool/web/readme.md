# DeepSeek 网页工具

- `DeepSeekWebApi.kt`：复用 DeepSeek API Key，调用官方 `/v1/tools/search` 与 `/v1/tools/fetch` 原始 JSON 接口，并统一处理 HTTP 错误。
- `DeepSeekWebSearchTool.kt`：常驻 `web_search` 工具，返回结构化搜索结果；默认建议只取摘要，避免网页全文占满上下文。
- `DeepSeekWebFetchTool.kt`：常驻 `web_fetch` 工具，把搜索得到的单个 HTTP/HTTPS 页面抓取成 Markdown 正文。

官方文档：https://api-docs.deepseek.com/zh-cn/guides/search/
