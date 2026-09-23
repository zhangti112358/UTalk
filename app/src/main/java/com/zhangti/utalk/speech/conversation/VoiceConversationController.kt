package com.zhangti.utalk.speech.conversation

import android.content.Context
import com.zhangti.utalk.agent.runtime.AgentEvent
import com.zhangti.utalk.agent.runtime.AgentEventListener
import com.zhangti.utalk.agent.runtime.AgentConversation
import com.zhangti.utalk.speech.asr.DoubaoStreamingAsrClient
import com.zhangti.utalk.speech.asr.StreamingAsrClient
import com.zhangti.utalk.speech.audio.MicrophonePcmSource
import com.zhangti.utalk.speech.audio.PcmAudioSource
import com.zhangti.utalk.speech.audio.PcmRingBuffer
import com.zhangti.utalk.speech.playback.PlaybackProgress
import com.zhangti.utalk.speech.playback.DefaultStreamingSpeechPlayerFactory
import com.zhangti.utalk.speech.playback.StreamingSpeechPlayer
import com.zhangti.utalk.speech.playback.StreamingSpeechPlayerFactory
import com.zhangti.utalk.speech.vad.ContinuousVadProcessor
import com.zhangti.utalk.speech.vad.SileroContinuousVadProcessor

enum class VoiceConversationState {
    STOPPED,
    LISTENING,
    SPEECH_DETECTED,
    RECOGNIZING,
    THINKING,
    SPEAKING,
    ERROR,
}

/**
 * 串联持续录音、VAD、ASR、Agent 和 TTS，但只依赖各模块公开接口。
 * 系统 AEC 已启用时，VAD 确认说话起点即打断播报；其他设备等 ASR 确认。
 * 模型仍在生成但尚未播音时不会中断模型。
 */
class VoiceConversationController(
    private val agent: AgentConversation,
    private val listener: Listener,
    private val audioSource: PcmAudioSource,
    private val vad: ContinuousVadProcessor,
    private val asr: StreamingAsrClient,
    private val playerFactory: StreamingSpeechPlayerFactory,
) : AutoCloseable {
    interface Listener {
        fun onStateChanged(state: VoiceConversationState)
        fun onPartialTranscript(text: String) {}
        fun onFinalTranscript(text: String) {}
        fun onAgentEvent(event: AgentEvent) {}
        fun onPlaybackInterrupted(progress: PlaybackProgress) {}
        fun onError(throwable: Throwable) {}
    }

    private val preRoll = PcmRingBuffer(PRE_ROLL_BYTES)
    private var active = false
    private var state = VoiceConversationState.STOPPED
    private var capturingAsr: StreamingAsrClient.Session? = null
    private var asrGeneration = 0L
    private var latestTranscript = ""
    private var agentRunning = false
    private var responseText = StringBuilder()
    private var player: StreamingSpeechPlayer? = null
    private var interruptedPlayback: PlaybackProgress? = null
    private var pendingTranscript: String? = null

    @Synchronized
    fun start() {
        if (active) return
        active = true
        changeStateLocked(VoiceConversationState.LISTENING)
        try {
            audioSource.start(object : PcmAudioSource.Listener {
                override fun onFrame(pcm: ByteArray) = handleAudioFrame(pcm)
                override fun onError(throwable: Throwable) = fail(throwable)
            })
        } catch (t: Throwable) {
            active = false
            fail(t)
            throw t
        }
    }

    @Synchronized
    fun stop() {
        if (!active && state == VoiceConversationState.STOPPED) return
        active = false
        audioSource.stop()
        capturingAsr?.cancel()
        capturingAsr = null
        if (agentRunning) agent.cancel()
        agentRunning = false
        player?.close()
        player = null
        preRoll.clear()
        pendingTranscript = null
        changeStateLocked(VoiceConversationState.STOPPED)
    }

    private fun handleAudioFrame(frame: ByteArray) {
        synchronized(this) {
            if (!active) return
            val bufferedBeforeCurrentFrame = preRoll.snapshot()
            val activity = try {
                vad.process(frame)
            } catch (t: Throwable) {
                failLocked(t)
                return
            }

            if (activity.changed && activity.isSpeech) {
                val currentPlayer = player
                val playbackCandidate = currentPlayer?.isAudible == true
                if (!agentRunning || playbackCandidate) {
                    changeStateLocked(VoiceConversationState.SPEECH_DETECTED)
                    // 已确认开启 AEC 的设备可以在 VAD 起点立即打断，不必等用户说完。
                    // ASR 仍会过滤可能漏进来的播报文本；无 AEC 时保留 ASR 确认。
                    if (currentPlayer != null &&
                        (!playbackCandidate || audioSource.isEchoCancellationActive)
                    ) {
                        interruptPlaybackLocked()
                    }
                    beginAsrLocked(bufferedBeforeCurrentFrame, playbackCandidate)
                }
            }

            capturingAsr?.let { session ->
                if (activity.isSpeech) session.sendPcm(frame)
            }

            if (activity.changed && !activity.isSpeech) {
                capturingAsr?.finish()
                if (capturingAsr != null) changeStateLocked(VoiceConversationState.RECOGNIZING)
                else if (!agentRunning) changeStateLocked(VoiceConversationState.LISTENING)
                capturingAsr = null
            }
            preRoll.append(frame)
        }
    }

    private fun beginAsrLocked(initialPcm: ByteArray, playbackCandidate: Boolean) {
        if (capturingAsr != null) return
        latestTranscript = ""
        val generation = ++asrGeneration
        val session = asr.start(object : StreamingAsrClient.Listener {
            override fun onResult(text: String, isFinal: Boolean) {
                synchronized(this@VoiceConversationController) {
                    if (!active || generation != asrGeneration) return
                    latestTranscript = text
                    if (!playbackCandidate || !EchoTranscriptFilter.isLikelyEcho(text, responseText.toString())) {
                        listener.onPartialTranscript(text)
                    }
                }
            }

            override fun onCompleted() {
                synchronized(this@VoiceConversationController) {
                    if (!active || generation != asrGeneration) return
                    val transcript = latestTranscript.trim()
                    latestTranscript = ""
                    if (playbackCandidate && EchoTranscriptFilter.isLikelyEcho(
                            transcript,
                            responseText.toString(),
                        )) {
                        when {
                            player?.isAudible == true -> changeStateLocked(VoiceConversationState.SPEAKING)
                            agentRunning -> changeStateLocked(VoiceConversationState.THINKING)
                            player == null -> changeStateLocked(VoiceConversationState.LISTENING)
                        }
                        return
                    }
                    if (transcript.isNotEmpty()) {
                        if (playbackCandidate && player != null) interruptPlaybackLocked()
                        submitTranscriptLocked(transcript)
                    }
                    else if (!agentRunning) changeStateLocked(VoiceConversationState.LISTENING)
                }
            }

            override fun onError(throwable: Throwable) {
                synchronized(this@VoiceConversationController) {
                    if (!active || generation != asrGeneration) return
                    capturingAsr = null
                    listener.onError(throwable)
                    if (!agentRunning) changeStateLocked(VoiceConversationState.LISTENING)
                }
            }
        })
        capturingAsr = session
        if (initialPcm.isNotEmpty()) session.sendPcm(initialPcm)
    }

    private fun interruptPlaybackLocked() {
        val currentPlayer = player ?: return
        val progress = currentPlayer.interrupt()
        player = null
        interruptedPlayback = progress
        listener.onPlaybackInterrupted(progress)
        recordInterruptionIfReadyLocked()
    }

    private fun submitTranscriptLocked(transcript: String) {
        listener.onFinalTranscript(transcript)
        if (agentRunning) {
            pendingTranscript = transcript
            changeStateLocked(VoiceConversationState.THINKING)
        } else {
            startAgentLocked(transcript)
        }
    }

    private fun startAgentLocked(transcript: String) {
        agentRunning = true
        responseText = StringBuilder()
        changeStateLocked(VoiceConversationState.THINKING)
        agent.send(transcript, AgentEventListener { event -> handleAgentEvent(event) })
    }

    private fun handleAgentEvent(event: AgentEvent) {
        synchronized(this) {
            if (!active) return
            listener.onAgentEvent(event)
            when (event) {
                is AgentEvent.TextDelta -> {
                    responseText.append(event.text)
                    if (interruptedPlayback == null) {
                        ensurePlayerLocked().appendText(event.text)
                    }
                }
                is AgentEvent.ToolStarted,
                is AgentEvent.ToolFinished -> if (player?.isAudible != true) {
                    changeStateLocked(VoiceConversationState.THINKING)
                }
                is AgentEvent.Completed -> {
                    agentRunning = false
                    if (responseText.isEmpty()) responseText.append(event.text)
                    player?.finish()
                    recordInterruptionIfReadyLocked()
                    if (player == null) runPendingOrListenLocked()
                }
                is AgentEvent.Failed -> {
                    agentRunning = false
                    player?.close()
                    player = null
                    listener.onError(RuntimeException(event.message))
                    recordInterruptionIfReadyLocked()
                    runPendingOrListenLocked()
                }
                AgentEvent.Cancelled -> {
                    agentRunning = false
                    player?.close()
                    player = null
                    recordInterruptionIfReadyLocked()
                    runPendingOrListenLocked()
                }
            }
        }
    }

    private fun ensurePlayerLocked(): StreamingSpeechPlayer {
        player?.let { return it }
        return playerFactory.create(object : StreamingSpeechPlayer.Listener {
            override fun onPlaybackStarted() {
                synchronized(this@VoiceConversationController) {
                    if (active) changeStateLocked(VoiceConversationState.SPEAKING)
                }
            }

            override fun onPlaybackCompleted() {
                synchronized(this@VoiceConversationController) {
                    player = null
                    runPendingOrListenLocked()
                }
            }

            override fun onPlaybackError(throwable: Throwable) {
                synchronized(this@VoiceConversationController) {
                    player = null
                    listener.onError(throwable)
                    if (!agentRunning) runPendingOrListenLocked()
                }
            }
        }).also {
            player = it
            it.start()
        }
    }

    private fun recordInterruptionIfReadyLocked() {
        if (agentRunning) return
        val progress = interruptedPlayback ?: return
        agent.recordPlaybackInterruption(
            spokenPrefix = progress.spokenPrefix,
            fullResponse = responseText.toString(),
        )
        interruptedPlayback = null
    }

    private fun runPendingOrListenLocked() {
        if (!active || agentRunning || player != null) return
        val next = pendingTranscript
        pendingTranscript = null
        if (next != null) startAgentLocked(next)
        else changeStateLocked(VoiceConversationState.LISTENING)
    }

    private fun changeStateLocked(newState: VoiceConversationState) {
        if (state == newState) return
        state = newState
        listener.onStateChanged(newState)
    }

    private fun fail(throwable: Throwable) = synchronized(this) { failLocked(throwable) }

    private fun failLocked(throwable: Throwable) {
        active = false
        audioSource.stop()
        capturingAsr?.cancel()
        capturingAsr = null
        player?.close()
        player = null
        state = VoiceConversationState.ERROR
        listener.onStateChanged(state)
        listener.onError(throwable)
    }

    override fun close() {
        stop()
        vad.close()
        audioSource.close()
    }

    companion object {
        fun create(
            context: Context,
            agent: AgentConversation,
            listener: Listener,
        ): VoiceConversationController = VoiceConversationController(
            agent = agent,
            listener = listener,
            audioSource = MicrophonePcmSource(context),
            vad = SileroContinuousVadProcessor(context.applicationContext),
            asr = DoubaoStreamingAsrClient(),
            playerFactory = DefaultStreamingSpeechPlayerFactory,
        )

        private const val PRE_ROLL_MS = 500
        private const val BYTES_PER_SECOND = 16_000 * 2
        private const val PRE_ROLL_BYTES = BYTES_PER_SECOND * PRE_ROLL_MS / 1000
    }
}
