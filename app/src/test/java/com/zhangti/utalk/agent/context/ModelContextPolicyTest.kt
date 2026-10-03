package com.zhangti.utalk.agent.context

import com.zhangti.utalk.agent.llm.LlmToolCall
import org.junit.Assert.*
import org.junit.Test

class ModelContextPolicyTest {
    private val call = LlmToolCall("call", "maps_search", "{\"query\":\"机场\"}")
    private val rawEstimator = ContextTokenEstimator { items -> TokenEstimate(
        system = items.filterIsInstance<SystemPromptContext>().sumOf { it.text.length.toLong() },
        dialogue = items.sumOf { when (it) {
            is UserInputContext -> it.text.length.toLong()
            is AssistantOutputContext -> it.text.orEmpty().length.toLong()
            else -> 0L
        } },
        toolHistory = items.sumOf { when (it) {
            is ToolResultContext -> it.content.length.toLong()
            is AssistantOutputContext -> it.toolCalls.sumOf { call -> call.arguments.length.toLong() }
            else -> 0L
        } },
    ) }
    private fun policy(recent: Int = 0, dialogueLimit: Long = 80, dialogueTarget: Long = 40) = ModelContextPolicy(
        estimator = rawEstimator, config = ContextBudgetConfig(softLimit = 100, toolEvictionTarget = 60,
            dialogueLimit = dialogueLimit, dialogueTarget = dialogueTarget, recentFullTurns = recent),
    )

    @Test fun oldToolsRemovedAsAChainButAllSpokenTextSurvives() {
        val original = AgentContextSnapshot(listOf(UserInputContext("旧问题"),
            AssistantOutputContext("我查一下", listOf(call)), ToolResultContext("call", "map", "x".repeat(150), false),
            AssistantOutputContext("旧回答"), UserInputContext("新问题")))
        val view = policy().transform(original).items
        assertFalse(view.any { it is ToolResultContext })
        assertFalse(view.filterIsInstance<AssistantOutputContext>().any { it.toolCalls.isNotEmpty() })
        assertTrue(view.contains(AssistantOutputContext("我查一下")))
        assertTrue(view.contains(AssistantOutputContext("旧回答")))
        assertEquals(5, original.items.size)
    }

    @Test fun defaultBudgetKeeps40kToolsAndEvictsOldChainOnlyAfter64k() {
        val items = mutableListOf<AgentContextItem>(UserInputContext("旧问题"),
            AssistantOutputContext("旧回答", listOf(call)),
            ToolResultContext("call", "map", "x".repeat(40_000), false))
        repeat(4) { items += UserInputContext("问题$it"); items += AssistantOutputContext("答") }
        val policy = ModelContextPolicy(estimator = rawEstimator)
        assertEquals(items, policy.transform(AgentContextSnapshot(items.toList())).items)
        items += ToolResultContext("current", "map", "x".repeat(25_000), false)
        val snapshot = AgentContextSnapshot(items.toList())
        val view = policy.transform(snapshot).items
        assertFalse(view.filterIsInstance<ToolResultContext>().any { it.toolCallId == "call" })
        assertTrue(view.filterIsInstance<ToolResultContext>().any { it.toolCallId == "current" })
        assertTrue(view.contains(UserInputContext("旧问题")))
        assertTrue(view.contains(AssistantOutputContext("旧回答")))
        assertTrue(rawEstimator.estimate(view).total <= 48_000)
        assertTrue(snapshot.items.filterIsInstance<ToolResultContext>().any { it.toolCallId == "call" })
    }

    @Test fun toolsRegisteredDuringATurnOnlyAppearOnce() {
        val availability = ToolAvailabilityContext("ride", ToolAvailabilitySource.DISCOVERED)
        val view = policy().transform(AgentContextSnapshot(listOf(UserInputContext("打车"), availability,
            availability, AssistantOutputContext("价格"), UserInputContext("选车")))).items
        assertEquals(1, view.filterIsInstance<ToolAvailabilityContext>().size)
        assertEquals(2, view.filterIsInstance<UserInputContext>().size)
    }

    @Test fun currentAndThreePreviousTurnsSurviveEvenAboveSoftBudget() {
        val items = (1..4).flatMap { listOf(UserInputContext("问题$it"), AssistantOutputContext("x".repeat(80))) }
        assertEquals(items, policy(recent = 3).transform(AgentContextSnapshot(items)).items)
    }

    @Test fun dialogueHasIndependentThresholdAndEvictedTextDoesNotReturn() {
        val original = listOf(UserInputContext("a".repeat(45)), AssistantOutputContext("b".repeat(45)), UserInputContext("当前"))
        val policy = policy()
        val first = policy.transform(AgentContextSnapshot(original)).items
        assertEquals(listOf(UserInputContext("当前")), first)
        val second = policy.transform(AgentContextSnapshot(original + AssistantOutputContext("回复"))).items
        assertFalse(second.contains(original.first()))
    }

    @Test fun unloadedToolsDoNotReappearAfterConversationGrows() {
        val original = listOf(UserInputContext("旧"), AssistantOutputContext(null, listOf(call)),
            ToolResultContext("call", "map", "x".repeat(150), false), UserInputContext("新"))
        val policy = policy()
        policy.transform(AgentContextSnapshot(original))
        val next = policy.transform(AgentContextSnapshot(original + AssistantOutputContext("答") + UserInputContext("再问")))
        assertFalse(next.items.any { it is ToolResultContext })
    }

    @Test fun pendingRideSurvivesUntilSuccessfulOrderEvenBeyondRecentWindow() {
        val original = mutableListOf<AgentContextItem>(UserInputContext("从家到机场"),
            AssistantOutputContext(null, listOf(call.copy(name = "ride__taxi_estimate"))),
            ToolResultContext("call", "ride__taxi_estimate", "trace和车型".repeat(30), false), AssistantOutputContext("选车型"))
        repeat(4) { original += UserInputContext("闲聊$it"); original += AssistantOutputContext("答") }
        original += UserInputContext("就选惊喜特价")
        val policy = policy(dialogueLimit = 10_000, dialogueTarget = 5_000)
        assertTrue(policy.transform(AgentContextSnapshot(original)).items.any { it is ToolResultContext })
        original += ToolResultContext("order", "ride__taxi_create_order", "成功", false)
        val done = policy.transform(AgentContextSnapshot(original)).items
        assertFalse(done.filterIsInstance<ToolResultContext>().any { it.toolName == "ride__taxi_estimate" })
    }

    @Test fun aNewSessionResetsEvictionState() {
        val policy = policy()
        policy.transform(AgentContextSnapshot(listOf(UserInputContext("a".repeat(90)), UserInputContext("b"))))
        val fresh = AgentContextSnapshot(listOf(UserInputContext("新会话"), AssistantOutputContext("答")))
        assertEquals(fresh, policy.transform(fresh))
    }
}
