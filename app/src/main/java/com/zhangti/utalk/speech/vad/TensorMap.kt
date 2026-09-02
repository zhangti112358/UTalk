package com.zhangti.utalk.speech.vad

import ai.onnxruntime.OnnxTensorLike
import java.io.Closeable

/**
 * 收集输入张量并统一释放资源的映射（参考 gkonovalov/android-vad，MIT License）。
 */
internal class TensorMap<K, V : OnnxTensorLike> : LinkedHashMap<K, V>(), Closeable {

    /** 以 `key to tensor` 语法放入张量，返回该张量。 */
    infix fun K.to(tensor: V): V {
        this@TensorMap[this] = tensor
        return tensor
    }

    override fun close() {
        values.forEach { it.close() }
    }
}
