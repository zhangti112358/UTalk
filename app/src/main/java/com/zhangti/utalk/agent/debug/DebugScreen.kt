package com.zhangti.utalk.agent.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

private fun sessionTime(millis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Activity 内的独立全屏调试视图；Agent 所在组合树持续存在。 */
@Composable
fun DebugScreen(repository: DebugRepository, currentSessionId: String?, agentStatus: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val revision by repository.changes.collectAsState()
    var selected by remember { mutableStateOf(currentSessionId) }
    var sessions by remember { mutableStateOf(emptyList<DebugSession>()) }
    var turns by remember { mutableStateOf(emptyList<DebugTurn>()) }
    var requests by remember { mutableStateOf(emptyList<DebugRequestSummary>()) }
    var sessionLimit by remember { mutableIntStateOf(50) }
    var itemLimit by remember(selected) { mutableIntStateOf(30) }
    var choosingSession by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(revision, selected, sessionLimit, itemLimit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val available = repository.sessions(sessionLimit)
                val id = selected ?: available.firstOrNull()?.id
                Triple(available, id?.let { repository.turns(it, itemLimit) }.orEmpty(),
                    id?.let { repository.requests(it, itemLimit) }.orEmpty())
            }
        }.onSuccess {
            sessions = it.first; turns = it.second; requests = it.third; error = ""
            if (selected == null) selected = sessions.firstOrNull()?.id
        }.onFailure {
            if (it is CancellationException) throw it
            error = "读取失败：${it.message ?: it.javaClass.simpleName}"
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("返回对话") }
                Text("Debug", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
            }
            Text("Agent：$agentStatus", style = MaterialTheme.typography.bodySmall)
            val session = sessions.firstOrNull { it.id == selected }
            OutlinedButton(onClick = { choosingSession = true }, modifier = Modifier.fillMaxWidth()) {
                Text(session?.let {
                    "${if (it.id == currentSessionId) "当前会话" else "历史会话"} · ${sessionTime(it.startedAt)} · ${it.turns} 轮 ▾"
                } ?: "选择会话")
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("完整历史") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("模型请求") })
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(if (tab == 0) "最新轮次在前，轮内按原始顺序排列。点击卡片展开；不展示检索用时间和定位。" else
                        "调用前快照，图片仅显示本地引用。旧会话未记录的请求无法还原。",
                        modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (tab == 0) {
                    items(turns, key = { "${selected}:${it.number}" }) { turn ->
                        selected?.let { TurnCard(repository, it, turn, revision) }
                    }
                    if (turns.isEmpty()) item { Text("暂无历史记录") }
                } else {
                    items(requests, key = { it.id }) { request -> RequestCard(repository, request, revision) }
                    if (requests.isEmpty()) item { Text("暂无模型请求快照；发起下一轮对话后会自动记录。") }
                }
                if ((if (tab == 0) turns.size else requests.size) >= itemLimit) item {
                    TextButton(onClick = { itemLimit += 30 }) { Text("加载更早记录") }
                }
            }
        }
    }
    if (choosingSession) AlertDialog(
        onDismissRequest = { choosingSession = false }, title = { Text("选择会话") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(sessions, key = { it.id }) { session ->
                    TextButton(onClick = { selected = session.id; choosingSession = false }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${sessionTime(session.startedAt)} · ${session.turns} 轮" +
                                if (session.id == currentSessionId) "（当前）" else "")
                            Text(session.preview.ifBlank { "尚未发送消息" }, maxLines = 2)
                        }
                    }
                }
                if (sessions.size >= sessionLimit) item {
                    TextButton(onClick = { sessionLimit += 50 }) { Text("加载更多会话") }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosingSession = false }) { Text("关闭") } },
    )
}

@Composable
private fun TurnCard(repository: DebugRepository, sessionId: String, turn: DebugTurn, revision: Long) {
    var expanded by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(emptyList<DebugEvent>()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(expanded, revision, sessionId, turn.number) {
        if (expanded) runCatching { withContext(Dispatchers.IO) { repository.events(sessionId, turn.number) } }
            .onSuccess { events = it; error = "" }.onFailure {
                if (it is CancellationException) throw it
                error = "读取失败：${it.message}"
            }
    }
    DebugExpandableCard(if (turn.number == 0) "会话初始化" else "第 ${turn.number} 轮",
        "${turn.eventCount} 条记录 · ${turn.preview}", expanded, onToggle = { expanded = !expanded }) {
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        val results = remember(events) { pairToolResults(events) }
        events.forEach { event ->
            key(event.id) {
                EventDetail(event, results)
            }
        }
    }
}

@Composable
private fun EventDetail(event: DebugEvent, results: Map<Pair<Long, Int>, DebugEvent>) {
    // 保持事件原始顺序：工具返回单独列出，不再挪到工具调用内部。
    if (event.kind != "assistant" || !event.content.isNullOrEmpty() || event.toolCalls.isEmpty()) {
        var expanded by remember(event.id) { mutableStateOf(false) }
        val title = eventTitle(event.kind) + if (event.kind == "tool_result")
            " · ${event.toolName.orEmpty()} · ${if (event.isError) "错误" else "成功"}" else ""
        DebugExpandableCard(title, event.content?.replace('\n', ' '), expanded,
            onToggle = { expanded = !expanded }, nested = true) {
            if (event.kind == "tool_result") DebugTextBlock("调用 ID", event.toolCallId.orEmpty())
            event.content?.takeIf { it.isNotEmpty() }?.let {
                DebugTextBlock(if (event.kind == "tool_result") "完整返回结果" else "原文", it,
                    json = event.kind in setOf("tool_result", "playback_interruption"))
            }
            event.imagePath?.let { DebugPhoto(it); DebugTextBlock("原图引用", it) }
        }
    }
    event.toolCalls.forEachIndexed { callIndex, call ->
        var expanded by remember(event.id, callIndex) { mutableStateOf(false) }
        val result = results[event.id to callIndex]
        DebugExpandableCard("工具调用 · ${call.name}",
            "${when { result == null -> "尚无返回记录"; result.isError -> "错误"; else -> "成功" }} · 点击查看参数",
            expanded, onToggle = { expanded = !expanded }, nested = true) {
            DebugTextBlock("调用 ID", call.id)
            DebugTextBlock("完整参数", call.arguments, json = true)
        }
    }
}

@Composable
private fun RequestCard(repository: DebugRepository, summary: DebugRequestSummary, revision: Long) {
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DebugRequest?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(expanded, summary.id, revision) {
        if (expanded) runCatching { withContext(Dispatchers.IO) { repository.request(summary.id) } }
            .onSuccess { detail = it; error = if (it == null) "记录不存在" else "" }.onFailure {
                if (it is CancellationException) throw it
                error = "读取失败：${it.message}"
            }
    }
    DebugExpandableCard("请求 #${summary.id} · 第 ${summary.turn} 轮",
        "${summary.messageCount} 条消息 · ${summary.toolCount} 个可用工具", expanded,
        onToggle = { expanded = !expanded }) {
        Text("移出 ${summary.removedItems} 条上下文 · 截断 ${summary.truncatedResults} 个工具结果")
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        detail?.let {
            val budget = remember(it.projection) { runCatching { JSONObject(it.projection).optJSONObject("token_budget") }.getOrNull() }
            budget?.optJSONObject("after")?.let { estimate ->
                Text("输入估算：${estimate.optLong("total")} Token（非实际计数）")
                Text("系统 ${estimate.optLong("system")} · 工具定义 ${estimate.optLong("tool_definitions")} · 对话 ${estimate.optLong("dialogue")} · 工具历史 ${estimate.optLong("tool_history")} · 图片 ${estimate.optLong("images")}",
                    style = MaterialTheme.typography.bodySmall)
            }
            val usage = remember(it.usage) { it.usage?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() } }
            Text(usage?.let { actual -> "实际用量：输入 ${actual.optLong("prompt_tokens")} · 输出 ${actual.optLong("completion_tokens")} · 合计 ${actual.optLong("total_tokens")}" }
                ?: "实际用量未收到（请求未结束、中断或旧版记录）；预算仍使用本地估算。",
                style = MaterialTheme.typography.bodySmall)
            DebugTextBlock("上下文裁剪记录", it.projection, json = true)
            it.usage?.let { raw -> DebugTextBlock("服务端实际用量", raw, json = true) }
            DebugTextBlock("消息、系统提示词、工具定义与模型参数", it.payload, json = true)
        }
    }
}

private fun eventTitle(kind: String): String = when (kind) {
    "user" -> "用户输入"
    "assistant" -> "模型输出"
    "system" -> "系统提示词"
    "tool_result" -> "工具返回"
    "tool_availability" -> "工具注册"
    "image" -> "照片"
    "playback_interruption" -> "语音播报打断"
    else -> kind
}
