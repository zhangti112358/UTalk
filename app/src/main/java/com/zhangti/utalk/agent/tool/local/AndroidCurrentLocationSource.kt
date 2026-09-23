package com.zhangti.utalk.agent.tool.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

fun interface LocationPermissionGate {
    suspend fun ensureGranted(): Boolean
}

/** 使用系统 LocationManager 获取新鲜定位；仅在一次工具调用期间监听。 */
class AndroidCurrentLocationSource(
    private val context: Context,
    private val permissionGate: LocationPermissionGate,
) : CurrentLocationSource {
    override suspend fun read(): DeviceLocation {
        if (!permissionGate.ensureGranted()) {
            throw IllegalStateException("未授予手机定位权限；请允许精确或大致位置后重试")
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) throw IllegalStateException("手机定位权限不可用")

        val providers = buildList {
            if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) throw IllegalStateException("手机定位服务未开启，或没有可用的定位来源")

        val fix = try {
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<Location> { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val ageMillis = (SystemClock.elapsedRealtimeNanos() -
                                location.elapsedRealtimeNanos) / 1_000_000
                            if (ageMillis !in 0L..30_000L) return
                            if (continuation.isActive) {
                                manager.removeUpdates(this)
                                continuation.resume(location)
                            }
                        }
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    try {
                        providers.forEach { provider ->
                            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                        }
                    } catch (error: Exception) {
                        manager.removeUpdates(listener)
                        continuation.cancel(error)
                    }
                }
            }
        } catch (error: SecurityException) {
            throw IllegalStateException("手机定位权限不足", error)
        } ?: throw IllegalStateException("20 秒内未取得新定位，请检查 GPS 或网络定位后重试")

        return DeviceLocation(
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyMeters = fix.accuracy,
            provider = fix.provider ?: "unknown",
            timestampMillis = fix.time,
        )
    }
}
