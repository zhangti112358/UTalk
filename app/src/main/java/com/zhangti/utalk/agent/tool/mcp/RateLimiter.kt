package com.zhangti.utalk.agent.tool.mcp

import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 令牌桶限流器：容量 = qps（允许瞬时突发 qps 个请求），每秒补充 qps 个令牌。
 * 用于遵守远程 MCP 服务的 QPS 限制；[acquire] 在无令牌时挂起等待补令牌。
 */
class RateLimiter(qps: Int) {

    private val qps = qps
    private val mutex = Mutex()
    private var tokens = qps.toDouble()
    private var lastRefillNs = System.nanoTime()

    init {
        require(qps > 0) { "qps 必须大于 0" }
    }

    /** 获取一个令牌；令牌不足时挂起，直到补充为止。 */
    suspend fun acquire() {
        mutex.withLock {
            while (true) {
                val now = System.nanoTime()
                val elapsedSec = (now - lastRefillNs) / 1_000_000_000.0
                tokens = min(qps.toDouble(), tokens + elapsedSec * qps)
                lastRefillNs = now

                if (tokens >= 1.0) {
                    tokens -= 1.0
                    return
                }
                // 缺多少令牌就等多少时间
                val waitMs = ((1.0 - tokens) / qps * 1000).toLong().coerceAtLeast(1)
                delay(waitMs)
            }
        }
    }
}
