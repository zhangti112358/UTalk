package com.zhangti.utalk.agent.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.zhangti.utalk.agent.history.SqliteHistoryStore

/** 从设置页进入的完整本机 Agent 调试记录。 */
class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    DebugScreen(
                        repository = SqliteHistoryStore.get(applicationContext),
                        currentSessionId = null,
                        agentStatus = "历史记录",
                        closeLabel = "返回设置",
                        onClose = ::finish,
                    )
                }
            }
        }
    }
}
