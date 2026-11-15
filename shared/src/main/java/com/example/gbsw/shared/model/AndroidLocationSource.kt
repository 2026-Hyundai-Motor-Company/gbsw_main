package com.example.gbsw.shared.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class AndroidLocationSource(private val context: Context) {
    fun hasPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @Suppress("MissingPermission")
    suspend fun current(): GeoPoint = withTimeout(15_000) {
        check(hasPermission()) { "위치 권한이 필요해요" }
        val manager = context.getSystemService(LocationManager::class.java)
        check(manager.isLocationEnabled) { "차량 설정에서 위치를 켜주세요" }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        check(providers.isNotEmpty()) { "사용 가능한 위치 정보가 없어요" }
        val recent = providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0..120_000_000_000L }
            .minByOrNull { it.accuracy }
        val location: Location? = recent ?: suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            manager.getCurrentLocation(providers.first(), cancellation, context.mainExecutor) {
                if (continuation.isActive) continuation.resume(it)
            }
        }
        check(location != null) { "현재 위치를 확인하지 못했어요. 다시 시도해주세요" }
        GeoPoint(location.latitude, location.longitude,
            if (location.hasBearing() && location.hasSpeed() && location.speed > 1) location.bearing.toDouble() else null)
    }
}
