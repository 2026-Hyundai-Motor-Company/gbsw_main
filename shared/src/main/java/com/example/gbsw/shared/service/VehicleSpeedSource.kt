package com.example.gbsw.shared.service

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.Speed
import kotlin.math.abs
import kotlin.math.roundToInt

class VehicleSpeedSource(private val context: CarContext, private val onSpeed: (Int?) -> Unit) {
    private var carInfo: CarInfo? = null
    private var listener: OnCarDataAvailableListener<Speed>? = null

    fun hasPermission() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        stop()
        if (!hasPermission() || context.carAppApiLevel < 3) { onSpeed(null); return }
        try {
            val info = context.getCarService(CarHardwareManager::class.java).carInfo
            val callback = OnCarDataAvailableListener<Speed> { speed ->
                val raw = speed.rawSpeedMetersPerSecond
                val value = raw.value
                onSpeed(if (raw.status == CarValue.STATUS_SUCCESS && value != null && value.isFinite())
                    (abs(value) * 3.6).roundToInt() else null)
            }
            carInfo = info
            listener = callback
            info.addSpeedListener(context.mainExecutor, callback)
        } catch (_: RuntimeException) {
            onSpeed(null)
        }
    }

    fun stop() {
        val callback = listener
        try { if (callback != null) carInfo?.removeSpeedListener(callback) } catch (_: RuntimeException) { }
        carInfo = null
        listener = null
    }

    companion object { const val PERMISSION = "android.car.permission.CAR_SPEED" }
}
