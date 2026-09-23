package com.zhangti.utalk.speech.conversation

import com.zhangti.utalk.agent.runtime.AgentCancellation
import com.zhangti.utalk.agent.runtime.AgentConversation
import com.zhangti.utalk.agent.runtime.AgentEvent
import com.zhangti.utalk.agent.runtime.AgentEventListener
import com.zhangti.utalk.speech.asr.StreamingAsrClient
import com.zhangti.utalk.speech.audio.PcmAudioSource
import com.zhangti.utalk.speech.playback.PlaybackProgress
import com.zhangti.utalk.speech.playback.StreamingSpeechPlayer
import com.zhangti.utalk.speech.playback.StreamingSpeechPlayerFactory
import com.zhangti.utalk.speech.vad.ContinuousVadProcessor
import com.zhangti.utalk.speech.vad.SpeechActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConversationControllerTest {
    @Test
    fun `active AEC interrupts at speech onset while ASR and model continue`() {
        val audio = FakeAudioSource(aecActive = true)
        val asr = FakeAsr()
        val agent = FakeAgent()
        val playerFactory = FakePlayerFactory()
        val listener = FakeVoiceListener()
        val controller = VoiceConversationController(
            agent = agent,
            listener = listener,
            audioSource = audio,
            vad = FakeVad(),
            asr = asr,
            playerFactory = playerFactory,
        )

        controller.start()
        audio.emit(byteArrayOf(1, 1))
        audio.emit(byteArrayOf(2, 2))
        asr.emitResult("你好", isFinal = true)
        asr.complete()
        agent.emit(AgentEvent.TextDelta("这是答案。"))
        val player = playerFactory.player
        player.audible = true

        audio.emit(byteArrayOf(1, 1))
        assertTrue(player.interrupted) // 不等待静音终点或 ASR 最终结果
        assertFalse(asr.session.finished)
        assertFalse(agent.cancelled)
        assertEquals(1, listener.interruptions.size)

        audio.emit(byteArrayOf(2, 2))
        asr.emitResult("等一下", isFinal = true)
        asr.complete()
        agent.emit(AgentEvent.Completed("这是答案。"))
        assertEquals("这是答案", agent.recordedPrefix)
        assertEquals("这是答案。", agent.recordedFullResponse)
        assertEquals("等一下", agent.lastInput)
        controller.close()
    }

    @Test
    fun `pre-roll reaches ASR and echo does not interrupt playback`() {
        val audio = FakeAudioSource()
        val vad = FakeVad()
        val asr = FakeAsr()
        val agent = FakeAgent()
        val playerFactory = FakePlayerFactory()
        val listener = FakeVoiceListener()
        val controller = VoiceConversationController(
            agent = agent,
            listener = listener,
            audioSource = audio,
            vad = vad,
            asr = asr,
            playerFactory = playerFactory,
        )

        controller.start()
        audio.emit(byteArrayOf(0, 0)) // 静音进入前置缓冲
        audio.emit(byteArrayOf(1, 1)) // 语音开始
        audio.emit(byteArrayOf(2, 2)) // 语音结束

        assertEquals(listOf(byteArrayOf(0, 0).toList(), byteArrayOf(1, 1).toList()), asr.session.sent.map { it.toList() })
        assertTrue(asr.session.finished)

        asr.emitResult("你好", isFinal = true)
        asr.complete()
        assertEquals("你好", agent.lastInput)

        agent.emit(AgentEvent.TextDelta("这是答案。"))
        val player = playerFactory.player
        assertTrue(player.started)
        assertEquals("这是答案。", player.text.toString())

        player.audible = true
        audio.emit(byteArrayOf(1, 1)) // 播放声音回灌麦克风
        assertFalse(player.interrupted) // VAD 命中本身不应打断
        audio.emit(byteArrayOf(2, 2))
        asr.emitResult("这是答案", isFinal = true)
        asr.complete()
        assertFalse(player.interrupted)
        assertEquals("你好", agent.lastInput)

        audio.emit(byteArrayOf(1, 1)) // 用户真正插话
        audio.emit(byteArrayOf(2, 2))
        asr.emitResult("我想去北京", isFinal = true)
        asr.complete()
        assertTrue(player.interrupted)
        assertFalse(agent.cancelled) // 插话不打断仍在运行的模型

        agent.emit(AgentEvent.Completed("这是答案。"))
        assertEquals("这是答案", agent.recordedPrefix)
        assertEquals("这是答案。", agent.recordedFullResponse)
        assertTrue(listener.interruptions.isNotEmpty())

        controller.close()
    }
}

private class FakeAudioSource(private val aecActive: Boolean = false) : PcmAudioSource {
    private var listener: PcmAudioSource.Listener? = null
    override var isRunning: Boolean = false
        private set
    override val isEchoCancellationActive: Boolean get() = isRunning && aecActive
    override fun start(listener: PcmAudioSource.Listener) {
        this.listener = listener
        isRunning = true
    }
    fun emit(frame: ByteArray) = listener!!.onFrame(frame)
    override fun stop() { isRunning = false }
    override fun close() = stop()
}

private class FakeVad : ContinuousVadProcessor {
    override fun process(frame: ByteArray): SpeechActivity = when (frame.first().toInt()) {
        1 -> SpeechActivity(isSpeech = true, changed = true)
        2 -> SpeechActivity(isSpeech = false, changed = true)
        else -> SpeechActivity(isSpeech = false, changed = false)
    }
    override fun close() = Unit
}

private class FakeAsr : StreamingAsrClient {
    lateinit var listener: StreamingAsrClient.Listener
    lateinit var session: FakeAsrSession
    override fun start(listener: StreamingAsrClient.Listener): StreamingAsrClient.Session {
        this.listener = listener
        session = FakeAsrSession()
        return session
    }
    fun emitResult(text: String, isFinal: Boolean) = listener.onResult(text, isFinal)
    fun complete() = listener.onCompleted()
}

private class FakeAsrSession : StreamingAsrClient.Session {
    val sent = mutableListOf<ByteArray>()
    var finished = false
    override fun sendPcm(pcm: ByteArray) { sent += pcm.copyOf() }
    override fun finish() { finished = true }
    override fun cancel() = Unit
}

private class FakeAgent : AgentConversation {
    private var listener: AgentEventListener? = null
    var lastInput: String? = null
    var cancelled = false
    var recordedPrefix: String? = null
    var recordedFullResponse: String? = null
    override val isRunning: Boolean get() = listener != null
    override fun send(text: String, listener: AgentEventListener): AgentCancellation {
        lastInput = text
        this.listener = listener
        return AgentCancellation()
    }
    fun emit(event: AgentEvent) {
        listener!!.onEvent(event)
        if (event is AgentEvent.Completed || event is AgentEvent.Failed || event is AgentEvent.Cancelled) listener = null
    }
    override fun cancel() { cancelled = true }
    override fun recordPlaybackInterruption(spokenPrefix: String, fullResponse: String) {
        recordedPrefix = spokenPrefix
        recordedFullResponse = fullResponse
    }
    override fun close() = Unit
}

private class FakePlayerFactory : StreamingSpeechPlayerFactory {
    lateinit var player: FakePlayer
    override fun create(listener: StreamingSpeechPlayer.Listener): StreamingSpeechPlayer {
        player = FakePlayer(listener)
        return player
    }
}

private class FakePlayer(private val listener: StreamingSpeechPlayer.Listener) : StreamingSpeechPlayer {
    var started = false
    var audible = false
    var interrupted = false
    val text = StringBuilder()
    override val isAudible: Boolean get() = audible
    override fun start() { started = true }
    override fun appendText(delta: String) { text.append(delta) }
    override fun finish() = Unit
    override fun interrupt(): PlaybackProgress {
        interrupted = true
        return PlaybackProgress("这是答案", text.toString(), 10, 12)
    }
    override fun close() = Unit
}

private class FakeVoiceListener : VoiceConversationController.Listener {
    val interruptions = mutableListOf<PlaybackProgress>()
    override fun onStateChanged(state: VoiceConversationState) = Unit
    override fun onPlaybackInterrupted(progress: PlaybackProgress) { interruptions += progress }
}
