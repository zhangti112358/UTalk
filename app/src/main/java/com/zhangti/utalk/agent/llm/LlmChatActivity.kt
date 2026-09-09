package com.zhangti.utalk.agent.llm

import android.os.Bundle
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

/**
 * DeepSeek 聊天测试页：输入文字 → 流式回复。
 * 生成中再点「发送」即中断；上下文在内存中保留，支持多轮对话。
 */
class LlmChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边 + 键盘 insets 处理：键盘弹出时消息列表保持可见可滚动
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LlmChatScreen()
                }
            }
        }
    }
}

@Composable
private fun LlmChatScreen() {
    var history by remember { mutableStateOf<List<LlmMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var streamingText by remember { mutableStateOf<String?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var currentStream by remember { mutableStateOf<LlmStream?>(null) }

    val llm = remember { DeepSeekLlm() }
    DisposableEffect(Unit) {
        onDispose {
            currentStream?.close()
            llm.close()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(history.size, streamingText) {
        listState.animateScrollToItem(maxOf(0, history.size + (if (streamingText != null) 1 else 0) - 1))
    }

    fun send() {
        if (streaming) {
            // 中断当前生成：拉取线程收尾后会把已生成的部分并入历史
            currentStream?.close()
            streaming = false
            status = "已中断"
            return
        }
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        val userMessage = LlmMessage(LlmRole.USER, text)
        val newHistory = history + userMessage
        history = newHistory
        streamingText = ""
        streaming = true
        status = "思考中…"

        val stream = llm.stream(LlmRequest(messages = newHistory))
        currentStream = stream

        // 后台拉取线程：把流式文本通过 mainHandler post 到主线程更新状态，
        // 保证每个分块都能触发重组（后台直接写 Compose 状态可能被快照机制吞掉，
        // 表现为「一次性输出」）。
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread({
            val sb = StringBuilder()
            var error: String? = null
            try {
                while (true) {
                    val chunk = stream.next() ?: break
                    chunk.text?.let { sb.append(it) }
                    val text = sb.toString()
                    mainHandler.post { streamingText = text }
                }
            } catch (e: Exception) {
                error = e.message ?: "未知错误"
            } finally {
                val finalText = sb.toString()
                val finalError = error
                mainHandler.post {
                    if (finalText.isNotEmpty()) {
                        history = newHistory + LlmMessage(LlmRole.ASSISTANT, finalText)
                    }
                    streamingText = null
                    streaming = false
                    currentStream = null
                    status = finalError?.let { "出错：$it" } ?: ""
                }
            }
        }, "LlmChatPull").start()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Text(
            "DeepSeek 聊天测试",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(history.size) { i ->
                ChatBubble(message = history[i])
            }
            if (streamingText != null) {
                item {
                    ChatBubble(
                        message = LlmMessage(LlmRole.ASSISTANT, streamingText ?: "…"),
                        streaming = true,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding() // 键盘弹出时输入栏跟随上移
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (streaming) "正在生成，发送即中断…" else "说点什么…") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { send() }) {
                Text(if (streaming) "停止" else "发送")
            }
        }
        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: LlmMessage, streaming: Boolean = false) {
    val isUser = message.role == LlmRole.USER
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = (message.content ?: "") + if (streaming) "▍" else "",
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
