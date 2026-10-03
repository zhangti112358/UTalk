package com.zhangti.utalk.agent.context

import com.zhangti.utalk.agent.llm.LlmTool
import org.junit.Assert.*
import org.junit.Test

class ContextTokenEstimatorTest {
    @Test fun defaultToolBudgetIs64kWith48kTargetWithoutChangingTextOrHardLimits() {
        val config = ContextBudgetConfig()
        assertEquals(64_000L, config.softLimit)
        assertEquals(48_000L, config.toolEvictionTarget)
        assertEquals(16_000L, config.dialogueLimit)
        assertEquals(8_000L, config.dialogueTarget)
        assertEquals(config, ModelContextPolicy().config)
        assertEquals(959_808L, config.hardInputLimit)
    }
    @Test fun actualSchemaAndDescriptionsAreCountedOnlyOnce() {
        val tool = LlmTool("search", "说明".repeat(100), mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string"))))
        val estimator = ConservativeTokenEstimator { tool }
        val item = ToolAvailabilityContext("search", ToolAvailabilitySource.CORE)
        val once = estimator.estimate(listOf(item))
        assertTrue(once.toolDefinitions > 200)
        assertEquals(once, estimator.estimate(listOf(item, item)))
    }
    @Test fun chineseEnglishImagesAndFixedPromptsHaveSeparateBudgets() {
        val estimator = ConservativeTokenEstimator()
        val estimate = estimator.estimate(listOf(SystemPromptContext("提示词"), UserInputContext("中文English123"),
            ToolResultContext("id", "map", "结果", false), ImageContext("/private/a.jpg", "now")))
        assertTrue(estimate.system > 0); assertTrue(estimate.dialogue > 0); assertTrue(estimate.toolHistory > 0)
        assertEquals(1536L, estimate.images)
        assertEquals(estimate.total, estimate.system + estimate.dialogue + estimate.toolHistory + estimate.images + estimate.other + estimate.overhead)
    }
    @Test fun hardLimitReservesOutputAndSafetyMargin() {
        assertEquals(959808L, ContextBudgetConfig().hardInputLimit)
    }
    @Test fun usageCalibrationOnlyIncreasesSafetyAndHasAnUpperBound() {
        val estimator = CalibratedTokenEstimator(ContextTokenEstimator { TokenEstimate(dialogue = 100) })
        estimator.observe(100, 150)
        assertTrue(estimator.estimate(emptyList()).total >= 165)
        val factor = estimator.factor
        estimator.observe(200, 100)
        assertEquals(factor, estimator.factor, 0.0001)
        estimator.observe(1, 1_000_000)
        assertEquals(4.0, estimator.factor, 0.0001)
    }
}
