package com.zhangti.utalk.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.zhangti.utalk.agent.tool.local.LocationPermissionGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把工具线程发起的定位授权请求交给当前 Agent 页面显示。 */
class ActivityLocationPermissionGate(private val activity: ComponentActivity) : LocationPermissionGate {
    private var pending: CompletableDeferred<Boolean>? = null
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        pending?.complete(
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        )
        pending = null
    }

    override suspend fun ensureGranted(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (isGranted()) return@withContext true
        val request = pending ?: CompletableDeferred<Boolean>().also {
            pending = it
            launcher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
        request.await()
    }

    fun close() {
        pending?.complete(false)
        pending = null
    }

    private fun isGranted(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }
}
