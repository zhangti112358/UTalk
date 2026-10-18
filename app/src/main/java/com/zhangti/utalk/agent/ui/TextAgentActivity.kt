package com.zhangti.utalk.agent.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zhangti.utalk.agent.runtime.AgentEvent
import com.zhangti.utalk.agent.runtime.AgentEventListener
import com.zhangti.utalk.agent.runtime.TextAgentSession
import com.zhangti.utalk.settings.SettingsActivity
import com.zhangti.utalk.speech.conversation.VoiceConversationController
import com.zhangti.utalk.speech.conversation.VoiceConversationState
import com.zhangti.utalk.speech.playback.PlaybackProgress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextAgentActivity : ComponentActivity() {
    private lateinit var locationPermissionGate: ActivityLocationPermissionGate
    private lateinit var photoCapture: PhotoCaptureCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionGate = ActivityLocationPermissionGate(this)
        photoCapture = PhotoCaptureCoordinator(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AgentScreen(locationPermissionGate, photoCapture)
                }
            }
        }
    }

    override fun onDestroy() {
        locationPermissionGate.close()
        photoCapture.close()
        super.onDestroy()
    }
}

private enum class LineRole { USER, ASSISTANT, EVENT }

private data class ChatLine(val id: Long, val role: LineRole, val text: String)

@Composable
private fun AgentScreen(locationPermissionGate: ActivityLocationPermissionGate, photoCapture: PhotoCaptureCoordinator) {
    val androidContext = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
            TextAgentSession.create(
                appContext = androidContext.applicationContext,
                locationPermissionGate = locationPermissionGate,
                photoCaptureSource = photoCapture,
            ) { progress -> mainHandler.post { status = progress } }
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
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("UTalk", style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    androidContext.startActivity(Intent(androidContext, SettingsActivity::class.java))
                }) { Text("设置") }
            }
            PhotoCapturePanel(photoCapture)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lines.isEmpty() && streamingText.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "有什么可以帮你？",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(lines, key = { it.id }) { ChatLineBubble(it) }
            if (streamingText.isNotEmpty()) {
                item { ChatLineBubble(ChatLine(-1, LineRole.ASSISTANT, "$streamingText▍")) }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val inputStatus = voicePartial.takeIf(String::isNotBlank)?.let { "识别中：$it" }
                ?: status.takeUnless { it == "就绪" || it.startsWith("已加载 ") }
            if (!inputStatus.isNullOrBlank()) {
                Text(
                    inputStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicrophoneToggle(
                    active = voiceEnabled,
                    enabled = ready && (!running || voiceEnabled),
                    onClick = { toggleVoice() },
                )
                Spacer(Modifier.width(10.dp))
                CompactChatInput(
                    value = input,
                    onValueChange = { input = it },
                    enabled = ready && !running && !voiceEnabled,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SendButton(
                    running = running,
                    enabled = ready && !voiceEnabled && (running || input.isNotBlank()),
                    onClick = { sendText() },
                )
            }
        }
    }
}

/** 单行时保持紧凑，多行时按内容增高；避免强压 Material 输入框导致文字裁切和空滚动。 */
@Composable
private fun CompactChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
    val borderColor = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = .38f)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        minLines = 1,
        maxLines = 4,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .heightIn(min = 48.dp, max = 112.dp)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** 紧凑的发送按钮：向上箭头表示发送，生成时切换成停止方块。 */
@Composable
private fun SendButton(running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val iconColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .background(background, CircleShape)
            .semantics { contentDescription = if (running) "停止生成" else "发送" },
    ) {
        Canvas(Modifier.size(21.dp)) {
            if (running) {
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(size.width * .28f, size.height * .28f),
                    size = Size(size.width * .44f, size.height * .44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            } else {
                val stroke = 2.2.dp.toPx()
                drawLine(iconColor, Offset(size.width * .5f, size.height * .80f), Offset(size.width * .5f, size.height * .22f), stroke, StrokeCap.Round)
                drawLine(iconColor, Offset(size.width * .5f, size.height * .22f), Offset(size.width * .22f, size.height * .50f), stroke, StrokeCap.Round)
                drawLine(iconColor, Offset(size.width * .5f, size.height * .22f), Offset(size.width * .78f, size.height * .50f), stroke, StrokeCap.Round)
            }
        }
    }
}

/** 持续语音开关：只保留话筒轮廓、支架和底座三笔，避免复杂图形。 */
@Composable
private fun MicrophoneToggle(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val iconColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .background(background, CircleShape)
            .semantics { contentDescription = if (active) "关闭语音" else "开启语音" },
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.dp.toPx()
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(size.width * .34f, size.height * .08f),
                size = Size(size.width * .32f, size.height * .50f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .16f),
                style = Stroke(stroke),
            )
            drawArc(
                color = iconColor,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * .20f, size.height * .24f),
                size = Size(size.width * .60f, size.height * .50f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawLine(iconColor, Offset(size.width * .5f, size.height * .74f), Offset(size.width * .5f, size.height * .91f), stroke, StrokeCap.Round)
            drawLine(iconColor, Offset(size.width * .35f, size.height * .91f), Offset(size.width * .65f, size.height * .91f), stroke, StrokeCap.Round)
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
