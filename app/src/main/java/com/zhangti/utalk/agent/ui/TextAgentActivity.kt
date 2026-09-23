package com.zhangti.utalk.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zhangti.utalk.agent.runtime.AgentEvent
import com.zhangti.utalk.agent.runtime.AgentEventListener
import com.zhangti.utalk.agent.runtime.TextAgentSession
import com.zhangti.utalk.speech.conversation.VoiceConversationController
import com.zhangti.utalk.speech.conversation.VoiceConversationState
import com.zhangti.utalk.speech.playback.PlaybackProgress
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
                    AgentScreen()
                }
            }
        }
    }
}

private enum class LineRole { USER, ASSISTANT, EVENT }

private data class ChatLine(val id: Long, val role: LineRole, val text: String)

@Composable
private fun AgentScreen() {
    val androidContext = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val sessionRef = remember { AtomicReference<TextAgentSession?>() }
    val voiceRef = remember { AtomicReference<VoiceConversationController?>() }
    var lines by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("正在加载出行工具…") }
    var voicePartial by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf(1L) }

    fun addLine(role: LineRole, text: String) {
        lines = lines + ChatLine(nextId++, role, text)
    }

    fun handleAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta -> streamingText += event.text
            is AgentEvent.ToolStarted -> {
                status = "正在调用 ${event.name}…"
                addLine(LineRole.EVENT, "调用工具：${event.name}")
            }
            is AgentEvent.ToolFinished -> {
                status = if (event.isError) "工具返回错误，正在处理…" else "工具完成，继续思考…"
            }
            is AgentEvent.Completed -> {
                val finalText = streamingText.ifBlank { event.text }
                if (finalText.isNotBlank()) addLine(LineRole.ASSISTANT, finalText)
                streamingText = ""
                running = false
                if (!voiceEnabled) status = "就绪"
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

    fun enableVoice() {
        val session = sessionRef.get() ?: return
        if (voiceRef.get() != null) return
        try {
            val controller = VoiceConversationController.create(
                context = androidContext,
                agent = session,
                listener = object : VoiceConversationController.Listener {
                    override fun onStateChanged(state: VoiceConversationState) {
                        mainHandler.post {
                            status = when (state) {
                                VoiceConversationState.STOPPED -> "语音模式已关闭"
                                VoiceConversationState.LISTENING -> "正在聆听…"
                                VoiceConversationState.SPEECH_DETECTED -> "检测到说话…"
                                VoiceConversationState.RECOGNIZING -> "正在识别…"
                                VoiceConversationState.THINKING -> "思考中…"
                                VoiceConversationState.SPEAKING -> "正在播报，可直接说话打断"
                                VoiceConversationState.ERROR -> "语音模式出错"
                            }
                        }
                    }

                    override fun onPartialTranscript(text: String) {
                        mainHandler.post { voicePartial = text }
                    }

                    override fun onFinalTranscript(text: String) {
                        mainHandler.post {
                            voicePartial = ""
                            streamingText = ""
                            running = true
                            addLine(LineRole.USER, text)
                        }
                    }

                    override fun onAgentEvent(event: AgentEvent) {
                        mainHandler.post { handleAgentEvent(event) }
                    }

                    override fun onPlaybackInterrupted(progress: PlaybackProgress) {
                        mainHandler.post {
                            addLine(
                                LineRole.EVENT,
                                "已打断播报；估算已听到：${progress.spokenPrefix.ifBlank { "（开头之前）" }}",
                            )
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        mainHandler.post {
                            addLine(LineRole.EVENT, "语音错误：${throwable.message ?: "未知错误"}")
                        }
                    }
                },
            )
            voiceRef.set(controller)
            voiceEnabled = true
            voicePartial = ""
            addLine(LineRole.EVENT, "语音模式已开启")
            controller.start()
        } catch (t: Throwable) {
            voiceRef.getAndSet(null)?.close()
            voiceEnabled = false
            status = "语音启动失败：${t.message ?: "未知错误"}"
        }
    }

    LaunchedEffect(Unit) {
        val created = withContext(Dispatchers.IO) {
            TextAgentSession.create { progress -> mainHandler.post { status = progress } }
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
        onDispose {
            voiceRef.getAndSet(null)?.close()
            sessionRef.getAndSet(null)?.close()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableVoice() else status = "未授予麦克风权限"
    }

    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, streamingText) {
        val count = lines.size + if (streamingText.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun toggleVoice() {
        if (voiceEnabled) {
            voiceRef.getAndSet(null)?.close()
            voiceEnabled = false
            running = false
            streamingText = ""
            voicePartial = ""
            status = "语音模式已关闭"
            addLine(LineRole.EVENT, "语音模式已关闭，仍可使用文字对话")
        } else if (ContextCompat.checkSelfPermission(
                androidContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableVoice()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun sendText() {
        val session = sessionRef.get()
        if (session == null || !ready || voiceEnabled) return
        if (running) {
            session.cancel()
            return
        }
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        streamingText = ""
        running = true
        status = "思考中…"
        addLine(LineRole.USER, text)
        session.send(text, AgentEventListener { event -> mainHandler.post { handleAgentEvent(event) } })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("UTalk Agent", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { toggleVoice() },
                    enabled = ready && (!running || voiceEnabled),
                ) {
                    Text(if (voiceEnabled) "关闭语音" else "开启语音")
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (voicePartial.isNotBlank()) {
                Text("识别中：$voicePartial", style = MaterialTheme.typography.bodyMedium)
            }
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
                enabled = ready && !running && !voiceEnabled,
                placeholder = {
                    Text(if (voiceEnabled) "语音模式正在持续聆听" else "问路线、航班、酒店、天气或打车…")
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { sendText() },
                enabled = ready && !voiceEnabled && (running || input.isNotBlank()),
            ) {
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
