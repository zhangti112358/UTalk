package com.zhangti.utalk.agent.ui

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import android.media.ExifInterface

@Composable
fun PhotoCapturePanel(coordinator: PhotoCaptureCoordinator) {
    coordinator.latestPhoto?.let { path ->
        val bitmap = remember(path) {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.let { original ->
                val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                if (degrees == 0) original else Bitmap.createBitmap(original, 0, 0,
                    original.width, original.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            }?.asImageBitmap()
        }
        bitmap?.let {
            Image(it, "本次会话最近拍摄的照片", Modifier.fillMaxWidth().height(100.dp))
            Text(coordinator.latestLens)
        }
    }
    if (coordinator.visible) {
        val context = LocalContext.current
        val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
        Dialog(onDismissRequest = coordinator::cancel) {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("正在自动拍照，请保持镜头朝向目标")
                    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(200.dp))
                    OutlinedButton(onClick = coordinator::cancel) { Text("取消拍照") }
                }
            }
        }
        DisposableEffect(view) {
            coordinator.open(view)
            onDispose { coordinator.releaseCamera() }
        }
    }
}
