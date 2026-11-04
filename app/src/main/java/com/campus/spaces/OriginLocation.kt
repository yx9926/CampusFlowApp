package com.campus.spaces

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal val originLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

internal fun Context.hasOriginLocationPermission() = originLocationPermissions.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

internal data class OriginCamera(val point: LatLng, val zoom: Float)

internal suspend fun currentOriginCamera(context: Context): OriginCamera? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    if (!LocationManagerCompat.isLocationEnabled(manager)) return null
    val providers = manager.getProviders(true).filter { it in listOf("fused", "gps", "network") }
    val recent = recentLocation(manager, providers)?.toOriginCamera(context, convertGps = true)
    return withTimeoutOrNull(25_000) {
        coroutineScope {
            val results = Channel<OriginCamera?>(2)
            val system = async {
                results.send(locationAttempt { currentLocation(context, manager, providers)?.toOriginCamera(context, true) })
            }
            val amap = async {
                results.send(locationAttempt { amapLocation(context)?.toOriginCamera(context, false) })
            }
            try {
                results.receive() ?: results.receive()
            } finally {
                system.cancel()
                amap.cancel()
                results.cancel()
            }
        }
    } ?: recent
}

private suspend fun locationAttempt(block: suspend () -> OriginCamera?): OriginCamera? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

@SuppressLint("MissingPermission")
private fun recentLocation(manager: LocationManager, providers: List<String>): Location? =
    providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .filter { location ->
            val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            age in 0..120_000_000_000L && location.hasAccuracy() && location.accuracy <= 1_000f
        }
        .maxByOrNull { it.elapsedRealtimeNanos }

private fun Location.toOriginCamera(context: Context, convertGps: Boolean): OriginCamera? {
    if (!validPoint(latitude, longitude)) return null
    val raw = LatLng(latitude, longitude)
    val point = if (convertGps) CoordinateConverter(context.applicationContext)
        .from(CoordinateConverter.CoordType.GPS)
        .coord(raw)
        .convert() ?: return null else raw
    if (!validPoint(point.latitude, point.longitude)) return null
    return OriginCamera(point, if (hasAccuracy() && accuracy <= 200f) 16f else 13f)
}

private suspend fun amapLocation(context: Context): Location? {
    val client = AMapLocationClient(context.applicationContext)
    try {
        return suspendCancellableCoroutine { continuation ->
            client.setLocationOption(AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isNeedAddress = false
                isOffset = true
                httpTimeOut = 15_000
            })
            client.setLocationListener { location ->
                if (continuation.isActive) continuation.resume(location?.takeIf { it.errorCode == 0 })
            }
            client.startLocation()
        }
    } finally {
        client.onDestroy()
    }
}

@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context, manager: LocationManager, providers: List<String>): Location? = coroutineScope {
    val results = Channel<Location?>(providers.size)
    val requests = providers.map { provider ->
        async {
            val location = suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        manager, provider, signal, ContextCompat.getMainExecutor(context),
                    ) { continuation.resume(it) }
                } catch (_: SecurityException) {
                    continuation.resume(null)
                } catch (_: IllegalArgumentException) {
                    continuation.resume(null)
                }
            }
            results.send(location)
        }
    }
    try {
        repeat(providers.size) {
            val location = results.receive()
            if (location != null) return@coroutineScope location
        }
        null
    } finally {
        requests.forEach { it.cancel() }
        results.cancel()
    }
}
