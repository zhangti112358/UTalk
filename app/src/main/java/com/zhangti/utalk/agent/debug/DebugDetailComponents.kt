package com.zhangti.utalk.agent.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 整个标题都是触控目标，正文可选择/复制，不会误触收起；长内容底部也可收起。 */
@Composable
internal fun DebugExpandableCard(
    title: String,
    summary: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    nested: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (nested) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
    )) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .clickable(role = Role.Button, onClickLabel = if (expanded) "收起" else "展开", onClick = onToggle)
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(if (expanded) "收起 ∧" else "展开 ∨", style = MaterialTheme.typography.labelMedium)
        }
        if (expanded) Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text("收起$title") }
        }
    }
}

/** 分段显示以避免巨大 JSON 卡住界面；复制始终使用完整原文。 */
@Composable
internal fun DebugTextBlock(title: String, raw: String, json: Boolean = false) {
    val context = LocalContext.current
    var expanded by remember(raw) { mutableStateOf(false) }
    val formatted by produceState(raw, raw, json, expanded) {
        value = if (json && expanded) withContext(Dispatchers.Default) {
            runCatching {
                when (val parsed = JSONTokener(raw).nextValue()) {
                    is JSONObject -> parsed.toString(2)
                    is JSONArray -> parsed.toString(2)
                    else -> raw
                }
            }.getOrDefault(raw)
        } else raw
    }
    var visibleChars by remember(raw) { mutableIntStateOf(6_000) }
    DebugExpandableCard(title, "${raw.length} 字 · 点击查看完整内容", expanded,
        onToggle = { expanded = !expanded }, nested = true) {
        TextButton(onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(title, raw))
        }) { Text("复制原文") }
        SelectionContainer {
            Text(formatted.take(visibleChars), style = MaterialTheme.typography.bodySmall,
                fontFamily = if (json) FontFamily.Monospace else FontFamily.Default)
        }
        if (visibleChars < formatted.length) TextButton(onClick = { visibleChars += 6_000 }) {
            Text("显示更多（${visibleChars}/${formatted.length} 字）")
        }
    }
}

@Composable
internal fun DebugPhoto(path: String) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val original = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
                    ?: return@runCatching null
                val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                if (degrees == 0) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height,
                    Matrix().apply { postRotate(degrees.toFloat()) }, true)
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), "历史照片", Modifier.fillMaxWidth().height(160.dp)) }
        ?: Text("照片暂不可预览")
}
