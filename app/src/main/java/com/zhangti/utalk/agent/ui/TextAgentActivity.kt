package com.zhangti.utalk.agent.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhangti.utalk.agent.runtime.AgentEvent
import com.zhangti.utalk.agent.runtime.AgentEventListener
import com.zhangti.utalk.agent.runtime.TextAgentSession
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextAgentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TextAgentScreen()
                }
            }
        }
    }
}

private enum class LineRole { USER, ASSISTANT, EVENT }

private data class ChatLine(
    val id: Long,
    val role: LineRole,
    val text: String,
)

@Composable
private fun TextAgentScreen() {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val sessionRef = remember { AtomicReference<TextAgentSession?>() }
    var lines by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("正在加载出行工具…") }
    var ready by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf(1L) }

    LaunchedEffect(Unit) {
        val created = withContext(Dispatchers.IO) {
            TextAgentSession.create { progress ->
                mainHandler.post { status = progress }
            }
        }
        sessionRef.set(created.first)
        ready = true
        status = if (created.second.errors.isEmpty()) {
            "已加载 ${created.second.loadedTools} 个工具"
        } else {
            "已就绪；${created.second.errors.size} 个服务连接失败，请检查手机网络后重新进入"
        }
    }

    DisposableEffect(Unit) {
        onDispose { sessionRef.getAndSet(null)?.close() }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, streamingText) {
        val count = lines.size + if (streamingText.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun addLine(role: LineRole, text: String) {
        lines = lines + ChatLine(nextId++, role, text)
    }

    fun send() {
        val text = input.trim()
        val session = sessionRef.get()
        if (text.isEmpty() || session == null || !ready) return
        if (running) {
            session.cancel()
            return
        }
        input = ""
        streamingText = ""
        running = true
        status = "思考中…"
        addLine(LineRole.USER, text)
        session.send(text, AgentEventListener { event ->
            mainHandler.post {
                when (event) {
                    is AgentEvent.TextDelta -> streamingText += event.text
                    is AgentEvent.ToolStarted -> {
                        status = "正在调用 ${event.name}…"
                        addLine(LineRole.EVENT, "调用工具：${event.name}")
                    }
                    is AgentEvent.ToolFinished -> {
                        status = if (event.isError) "工具返回错误，正在交给模型处理…" else "工具完成，继续思考…"
                    }
                    is AgentEvent.Completed -> {
                        val finalText = streamingText.ifBlank { event.text }
                        if (finalText.isNotBlank()) addLine(LineRole.ASSISTANT, finalText)
                        streamingText = ""
                        running = false
                        status = "就绪"
                    }
                    is AgentEvent.Failed -> {
                        if (streamingText.isNotBlank()) addLine(LineRole.ASSISTANT, streamingText)
                        streamingText = ""
                        addLine(LineRole.EVENT, "出错：${event.message}")
                        running = false
                        status = "出错"
                    }
                    AgentEvent.Cancelled -> {
                        if (streamingText.isNotBlank()) addLine(LineRole.ASSISTANT, streamingText)
                        streamingText = ""
                        running = false
                        status = "已停止"
                    }
                }
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("UTalk 文字 Agent", style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lines, key = { it.id }) { ChatLineBubble(it) }
            if (streamingText.isNotEmpty()) {
                item { ChatLineBubble(ChatLine(-1, LineRole.ASSISTANT, "$streamingText▍")) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = ready && !running,
                placeholder = { Text(if (ready) "问路线、航班、酒店、天气或打车…" else "正在初始化…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { send() }, enabled = ready && (running || input.isNotBlank())) {
                Text(if (running) "停止" else "发送")
            }
        }
    }
}

@Composable
private fun ChatLineBubble(line: ChatLine) {
    val alignment = when (line.role) {
        LineRole.USER -> Alignment.CenterEnd
        LineRole.ASSISTANT -> Alignment.CenterStart
        LineRole.EVENT -> Alignment.Center
    }
    val color = when (line.role) {
        LineRole.USER -> MaterialTheme.colorScheme.primaryContainer
        LineRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
        LineRole.EVENT -> MaterialTheme.colorScheme.secondaryContainer
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Text(
            text = line.text,
            style = if (line.role == LineRole.EVENT) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .background(color, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
