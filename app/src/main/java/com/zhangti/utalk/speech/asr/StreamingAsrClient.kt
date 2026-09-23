package com.zhangti.utalk.speech.asr

/** 语音会话层依赖的流式识别接口，豆包协议细节留在适配实现中。 */
interface StreamingAsrClient {
    fun start(listener: Listener): Session

    interface Listener {
        fun onResult(text: String, isFinal: Boolean)
        fun onCompleted() {}
        fun onError(throwable: Throwable) {}
    }

    interface Session {
        fun sendPcm(pcm: ByteArray)
        fun finish()
        fun cancel()
    }
}

class DoubaoStreamingAsrClient(
    private val client: DoubaoAsr = DoubaoAsr(),
) : StreamingAsrClient {
    override fun start(listener: StreamingAsrClient.Listener): StreamingAsrClient.Session {
        val session = client.startStream(object : DoubaoAsr.Listener {
            override fun onResult(text: String, isFinal: Boolean) = listener.onResult(text, isFinal)
            override fun onCompleted() = listener.onCompleted()
            override fun onError(throwable: Throwable) = listener.onError(throwable)
        })
        return object : StreamingAsrClient.Session {
            override fun sendPcm(pcm: ByteArray) = session.sendPcm(pcm)
            override fun finish() = session.finish()
            override fun cancel() = session.cancel()
        }
    }
}

