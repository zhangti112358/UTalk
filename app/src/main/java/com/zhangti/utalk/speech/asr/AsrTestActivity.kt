package com.zhangti.utalk.speech.asr

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * 豆包流式 ASR 手动测试页：麦克风实时采集 → 边说边出字。
 */
class AsrTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AsrTestScreen()
                }
            }
        }
    }
}

/**
 * 一次「麦克风 → 豆包 ASR」会话：启动采集线程，按 100ms 一包送入 [DoubaoAsr.Session]。
 * 连接未就绪时的音频包由 Session 自动暂存、就绪后补发。
 */
private class MicAsrSession(
    private val asr: DoubaoAsr,
    private val listener: DoubaoAsr.Listener,
) {
    private var session: DoubaoAsr.Session? = null
    @Volatile private var capturing = false

    fun start() {
        session = asr.startStream(listener)
        startCapture()
    }

    /** 用户点停止：结束采集，通知 ASR 音频已结束，等待收尾回调。 */
    fun stop() {
        stopCapture()
        session?.finish()
    }

    fun close() {
        stopCapture()
        session?.cancel()
        session = null
    }

    private fun startCapture() {
        val sampleRate = AsrConstants.SAMPLE_RATE
        val segmentBytes = sampleRate * 2 * 100 / 1000 // 100ms 一包
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, segmentBytes * 2),
        )
        capturing = true
        Thread({
            try {
                recorder.startRecording()
                val chunk = ByteArray(segmentBytes)
                while (capturing) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) session?.sendPcm(chunk, read)
                }
            } catch (_: Exception) {
                // 会话已关闭等异常，静默结束
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }, "AsrMicCapture").start()
    }

    private fun stopCapture() {
        capturing = false // 采集线程自行退出并释放 recorder
    }
}

@Composable
private fun AsrTestScreen() {
    val context = LocalContext.current
    var session by remember { mutableStateOf<MicAsrSession?>(null) }
    var recording by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("未开始") }
    var partialText by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { session?.close() }
    }

    fun startSession() {
        val newSession = MicAsrSession(
            asr = DoubaoAsr(), // API Key 走 AppConfig（secrets.properties）
            listener = object : DoubaoAsr.Listener {
                // 注意：回调在 OkHttp WS 线程，Compose state 线程安全可直接更新
                override fun onResult(text: String, isFinal: Boolean) {
                    status = "识别中…"
                    partialText = text
                    if (isFinal) finalText = text
                }

                override fun onError(throwable: Throwable) {
                    status = "出错"
                    errorText = throwable.message ?: "未知错误"
                    recording = false
                    session = null
                }

                override fun onCompleted() {
                    status = "识别完成"
                    recording = false
                    session = null
                }
            }
        )
        session = newSession
        recording = true
        errorText = ""
        finalText = ""
        partialText = ""
        newSession.start()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSession() else status = "未授予麦克风权限"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("豆包流式 ASR 测试", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("状态：$status", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (session != null) {
                    recording = false
                    session?.stop()
                } else {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startSession()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        ) {
            Text(if (recording) "停止" else "开始")
        }
        Spacer(Modifier.height(24.dp))

        Text("实时结果", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = partialText.ifEmpty { "（等待识别结果…）" },
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(16.dp))

        Text("最终结果", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(text = finalText.ifEmpty { "（无）" })
        Spacer(Modifier.height(16.dp))

        if (errorText.isNotEmpty()) {
            Text(text = errorText, color = MaterialTheme.colorScheme.error)
        }
    }
}
