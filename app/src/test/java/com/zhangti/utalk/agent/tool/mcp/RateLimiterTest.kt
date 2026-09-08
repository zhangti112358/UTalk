package com.zhangti.utalk.agent.tool.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RateLimiter 令牌桶单元测试。
 */
class RateLimiterTest {

    @Test
    fun `qps=2 - burst of 2 then waits for token`() = runBlocking {
        val limiter = RateLimiter(qps = 2)
        val start = System.nanoTime()

        limiter.acquire() // 突发第 1 个，不等待
        limiter.acquire() // 突发第 2 个，不等待
        val afterBurstMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("突发调用不应等待，实际 ${afterBurstMs}ms", afterBurstMs < 100)

        limiter.acquire() // 令牌耗尽，需等约 500ms 补 1 个
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("第 3 次调用应至少等待约 400ms，实际 ${elapsedMs}ms", elapsedMs >= 400)
        assertTrue("等待不应超过 1500ms，实际 ${elapsedMs}ms", elapsedMs < 1500)
    }

    @Test
    fun `qps=1 - consecutive calls spaced one second`() = runBlocking {
        val limiter = RateLimiter(qps = 1)
        val start = System.nanoTime()

        limiter.acquire()
        limiter.acquire() // 需等约 1s

        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("连续调用应间隔约 1s，实际 ${elapsedMs}ms", elapsedMs in 800..1500)
    }

    @Test
    fun `qps must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { RateLimiter(0) }
    }

    @Test
    fun `config qps must be positive and url http`() {
        assertThrows(IllegalArgumentException::class.java) {
            McpServerConfig(name = "bad", url = "https://x", qps = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpServerConfig(name = "bad", url = "ws://x", qps = 1)
        }
    }
}
