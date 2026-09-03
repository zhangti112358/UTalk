package com.zhangti.utalk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhangti.utalk.speech.asr.AsrTestActivity
import com.zhangti.utalk.speech.tts.TtsTestActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var count by remember { mutableIntStateOf(0) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "UTalk",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Hello Compose 👋",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { count++ }) {
                            Text("点击了 $count 次")
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            startActivity(Intent(this@MainActivity, AsrTestActivity::class.java))
                        }) {
                            Text("测试 ASR")
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            startActivity(Intent(this@MainActivity, TtsTestActivity::class.java))
                        }) {
                            Text("测试 TTS")
                        }
                    }
                }
            }
        }
    }
}
