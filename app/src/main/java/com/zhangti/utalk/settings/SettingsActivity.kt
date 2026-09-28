package com.zhangti.utalk.settings

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zhangti.utalk.LocalConfig

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // API key 不进入最近任务缩略图或系统截图。
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    var keys by remember {
        mutableStateOf(SettingsCatalog.apiKeys.associate { it.key to SettingsRepository.savedKey(it.key).orEmpty() })
    }
    var didiEnvironment by remember { mutableStateOf(SettingsRepository.didiEnvironment()) }
    var pendingProduction by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Text("更改后，重新打开 Agent 或语音页面生效。未填写的密钥会使用本地构建配置。")

        Spacer(Modifier.height(4.dp))
        Text("滴滴 MCP 环境", style = MaterialTheme.typography.titleMedium)
        Text(if (didiEnvironment == DiDiEnvironment.SANDBOX) {
            "当前：测试环境。只创建模拟订单，不会真的叫车。"
        } else {
            "当前：正式环境。明确叫车指令会直接创建真实订单。"
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                SettingsRepository.setDiDiEnvironment(DiDiEnvironment.SANDBOX)
                didiEnvironment = DiDiEnvironment.SANDBOX
                status = "已切换测试环境；重新打开 Agent 后生效"
            }) { Text("测试环境") }
            OutlinedButton(onClick = { pendingProduction = true }) { Text("正式环境") }
        }

        Spacer(Modifier.height(4.dp))
        Text("API key", style = MaterialTheme.typography.titleMedium)
        SettingsCatalog.apiKeys.forEach { field ->
            OutlinedTextField(
                value = keys[field.key].orEmpty(),
                onValueChange = { keys = keys + (field.key to it) },
                label = { Text("${field.label} · ${field.key}") },
                supportingText = {
                    Text(if (keys[field.key].isNullOrEmpty() && LocalConfig[field.key].isNotBlank()) {
                        "留空使用本地构建配置"
                    } else "留空清除手机保存值；密钥只保存在本机")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = {
            status = runCatching {
                SettingsRepository.saveKeys(keys)
                "密钥已保存；重新打开 Agent 或语音页面生效"
            }.getOrElse { "保存失败：${it.javaClass.simpleName}" }
        }) { Text("保存 API key") }

        Spacer(Modifier.height(4.dp))
        Text("批量导入", style = MaterialTheme.typography.titleMedium)
        Text("粘贴 secrets.properties 的多行 key=value 内容，提取后检查各项，再点保存。")
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it },
            label = { Text("粘贴密钥文本") },
            visualTransformation = MultilinePasswordTransformation,
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = {
            status = runCatching {
                val imported = SettingsCatalog.parseImport(importText)
                if (imported.isEmpty()) "未识别到有效密钥；请检查 key=value 名称"
                else {
                    keys = keys + imported
                    importText = ""
                    "已提取 ${imported.size} 项并填入上方，请点击“保存 API key”"
                }
            }.getOrElse { "解析失败：请检查是否为 key=value 格式" }
        }) { Text("提取并填入") }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
    }

    if (pendingProduction) AlertDialog(
        onDismissRequest = { pendingProduction = false },
        title = { Text("切换滴滴正式环境？") },
        text = { Text("正式环境会产生真实叫车订单。当前 Agent 对明确的叫车指令不会再进行二次确认；请确认你希望启用真实下单。") },
        confirmButton = {
            TextButton(onClick = {
                SettingsRepository.setDiDiEnvironment(DiDiEnvironment.PRODUCTION)
                didiEnvironment = DiDiEnvironment.PRODUCTION
                pendingProduction = false
                status = "已切换正式环境；重新打开 Agent 后生效"
            }) { Text("启用真实下单") }
        },
        dismissButton = { TextButton(onClick = { pendingProduction = false }) { Text("保持测试环境") } },
    )
}

/** 保留换行，让大段密钥文本能逐行粘贴并编辑，但不在屏幕上明文显示。 */
private object MultilinePasswordTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        AnnotatedString(text.text.map { if (it == '\n') '\n' else '•' }.joinToString("")),
        OffsetMapping.Identity,
    )
}
