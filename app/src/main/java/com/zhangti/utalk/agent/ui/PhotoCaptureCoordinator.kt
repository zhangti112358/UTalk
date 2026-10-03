package com.zhangti.utalk.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import com.google.common.util.concurrent.ListenableFuture
import com.zhangti.utalk.agent.tool.local.CapturedPhoto
import com.zhangti.utalk.agent.tool.local.PhotoCaptureSource
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 将拍摄请求桥接到前台页面；相机生命周期与 Agent/模型调用相互独立。 */
class PhotoCaptureCoordinator(private val activity: ComponentActivity) : PhotoCaptureSource {
    var visible by mutableStateOf(false)
        private set
    var latestPhoto by mutableStateOf<String?>(null)
        private set
    var latestLens by mutableStateOf("")
        private set
    var latestGalleryStatus by mutableStateOf("")
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val executor = ContextCompat.getMainExecutor(activity)
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
    private val gallerySaver = PhotoGallerySaver(activity.applicationContext)
    private val directory = File(activity.filesDir, "agent_photos/${UUID.randomUUID()}")
    private var pending: CompletableDeferred<CapturedPhoto>? = null
    private var permission: CompletableDeferred<Boolean>? = null
    private var storagePermission: CompletableDeferred<Boolean>? = null
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var captureJob: Job? = null
    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission?.complete(it)
        permission = null
    }
    private val storagePermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        storagePermission?.complete(it)
        storagePermission = null
    }

    override suspend fun capture(): CapturedPhoto = withContext(Dispatchers.Main.immediate) {
        check(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { "请保持 Agent 页面在前台后拍照" }
        check(pending == null) { "已有拍照任务进行中" }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            val granted = permission ?: CompletableDeferred<Boolean>().also {
                permission = it
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            check(granted.await()) { "未授予相机权限，无法拍照" }
        }
        val resumed = withTimeoutOrNull(5_000) {
            while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) delay(50)
            true
        }
        check(resumed == true) { "请保持 Agent 页面在前台后拍照" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED) {
            val granted = CompletableDeferred<Boolean>()
            storagePermission = granted
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            granted.await()
        }
        val request = CompletableDeferred<CapturedPhoto>()
        pending = request
        visible = true
        val photo = try {
            withTimeoutOrNull(30_000) { request.await() }
                ?: throw IllegalStateException("拍照超时，请保持页面在前台后重试")
        } finally {
            pending = null
            visible = false
            releaseCamera()
        }
        val saved = withContext(Dispatchers.IO) { runCatching { gallerySaver.save(File(photo.path)) } }
        latestGalleryStatus = saved.fold(
            onSuccess = { "已保存到系统相册" },
            onFailure = { "相册保存失败：${it.message ?: "未知错误"}" },
        )
        photo.copy(galleryUri = saved.getOrNull()?.toString(), galleryError = saved.exceptionOrNull()?.let {
            it.message ?: it.javaClass.simpleName
        })
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    @Suppress("DEPRECATION")
    fun open(view: PreviewView) {
        val request = pending ?: return
        val openedAt = SystemClock.elapsedRealtime()
        captureJob = scope.launch {
            try {
                val cameraProvider = cameraProviderFuture.await(cancelFutureOnCancellation = false)
                if (pending !== request || !visible) return@launch
                provider = cameraProvider
                val rear = cameraProvider.availableCameraInfos.filter {
                    Camera2CameraInfo.from(it).getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK
                }
                check(rear.isNotEmpty()) { "手机没有可访问的后置相机" }
                // 在系统实际暴露的后置相机中优先选择视角最宽的，不硬编码厂商 camera ID。
                val widest = rear.maxByOrNull { info ->
                    val characteristics = Camera2CameraInfo.from(info)
                    val width = characteristics.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0f
                    val focal = characteristics.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 1f
                    width / focal
                }!!
                val selector = CameraSelector.Builder().addCameraFilter { infos -> infos.filter { it == widest } }.build()
                val p = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val c = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1600, 1200))
                    .setJpegQuality(90)
                    .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                preview = p
                capture = c
                val camera = cameraProvider.bindToLifecycle(activity, selector, p, c)
                val zoom = camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                val zoomUpdate = camera.cameraControl.setZoomRatio(zoom)
                // 一个共享等待预算；相机已就绪时立即拍摄，不做固定倒计时。
                val remainingPreparationMillis = (1_500L - (SystemClock.elapsedRealtime() - openedAt)).coerceAtLeast(0)
                if (remainingPreparationMillis > 0) withTimeoutOrNull(remainingPreparationMillis) {
                    zoomUpdate.await()
                    awaitPreview(view)
                    if (view.width > 0 && view.height > 0) {
                        val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
                        camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build()).await()
                    }
                }
                if (pending !== request || !visible) return@launch
                directory.mkdirs()
                val file = File.createTempFile("photo_", ".jpg", directory)
                val lens = if (zoom < 1f || rear.size > 1) "后置最广视角（${zoom}×）" else "后置主摄（超广角未向应用开放，已回退）"
                Log.d("UTalkCamera", "Shutter requested after ${SystemClock.elapsedRealtime() - openedAt} ms")
                c.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            if (pending === request && visible) {
                                Log.d("UTalkCamera", "Photo saved after ${SystemClock.elapsedRealtime() - openedAt} ms")
                                latestPhoto = file.absolutePath
                                latestLens = lens
                                latestGalleryStatus = "正在保存到系统相册…"
                                request.complete(CapturedPhoto(file.absolutePath, Instant.now().toString(), lens))
                            } else file.delete()
                        }
                        override fun onError(error: ImageCaptureException) {
                            file.delete()
                            request.completeExceptionally(IllegalStateException("拍照失败：${error.message}", error))
                        }
                    })
            } catch (error: Exception) {
                request.completeExceptionally(IllegalStateException("相机启动或拍摄失败：${error.message}", error))
            }
        }
    }

    private suspend fun awaitPreview(view: PreviewView) = suspendCancellableCoroutine<Unit> { continuation ->
        val observer = object : Observer<PreviewView.StreamState> {
            override fun onChanged(value: PreviewView.StreamState) {
                if (value == PreviewView.StreamState.STREAMING && continuation.isActive) {
                    view.previewStreamState.removeObserver(this)
                    continuation.resume(Unit)
                }
            }
        }
        continuation.invokeOnCancellation { executor.execute { view.previewStreamState.removeObserver(observer) } }
        view.previewStreamState.observe(activity, observer)
    }

    fun cancel() { pending?.completeExceptionally(IllegalStateException("用户取消拍照")) }

    fun releaseCamera() {
        captureJob?.cancel()
        captureJob = null
        preview?.let { provider?.unbind(it) }
        capture?.let { provider?.unbind(it) }
        preview = null
        capture = null
    }

    fun close() {
        cancel()
        pending = null
        visible = false
        permission?.complete(false)
        storagePermission?.complete(false)
        scope.cancel()
        releaseCamera()
        // 已进入本机历史的原图必须跨会话保留；这里只释放相机，不删除照片。
    }

    private suspend fun <T> ListenableFuture<T>.await(cancelFutureOnCancellation: Boolean = true): T = suspendCancellableCoroutine { continuation ->
        addListener({
            if (continuation.isActive) {
                try { continuation.resume(get()) } catch (error: Exception) { continuation.resumeWithException(error) }
            }
        }, executor)
        if (cancelFutureOnCancellation) continuation.invokeOnCancellation { cancel(true) }
    }
}
