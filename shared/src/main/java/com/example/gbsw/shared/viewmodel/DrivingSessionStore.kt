package com.example.gbsw.shared.viewmodel

import com.example.gbsw.shared.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI 수명과 분리된 상태 저장소. 실시간 입력과 시연 입력은 별도로 보관한다. */
class DrivingSessionStore(
    val demoAvailable: Boolean,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val places: DemoPlaceRepository = DemoPlaceRepository(),
    initialDrivingMillis: Long = 0,
    initialCooldowns: Map<DrivingWarning, Long> = emptyMap(),
) {
    private val mutableState = MutableStateFlow(DrivingState(drivingMillis = initialDrivingMillis.coerceAtLeast(0)))
    val uiState = mutableState.asStateFlow()
    private var realState = mutableState.value
    private val realAcknowledgedAt = mutableMapOf<DrivingWarning, Long>()
    private val demoAcknowledgedAt = mutableMapOf<DrivingWarning, Long>()
    private var demoOffsetMillis = 0L
    private var lastTick = clockMillis()
    private var lastSpeedSample: Long? = null

    init {
        initialCooldowns.forEach { (warning, remaining) ->
            if (remaining > 0) realAcknowledgedAt[warning] =
                clockMillis() - warning.cooldownMinutes * 60_000L + remaining.coerceAtMost(warning.cooldownMinutes * 60_000L)
        }
    }

    fun places(category: PlaceCategory): List<Place> =
        if (uiState.value.isDemo) places.places(category) else emptyList()

    fun startDemo() {
        if (!demoAvailable) return
        tick()
        if (!uiState.value.isDemo) realState = uiState.value
        demoAcknowledgedAt.clear()
        demoOffsetMillis = 0
        update(DrivingState(drivingMillis = 72 * 60_000L, speedKph = 85, isDemo = true))
    }

    fun stopDemo() {
        if (!uiState.value.isDemo) return
        demoAcknowledgedAt.clear()
        demoOffsetMillis = 0
        lastTick = clockMillis()
        lastSpeedSample = null
        update(realState.copy(speedKph = null, stoppedMillis = 0))
    }

    fun driveFor(minutes: Int) {
        require(minutes >= 0)
        val current = uiState.value
        if (!current.isDemo) return
        demoOffsetMillis += minutes * 60_000L
        update(current.copy(drivingMillis = current.drivingMillis + minutes * 60_000L, stoppedMillis = 0))
    }

    fun stopFor(minutes: Int) {
        require(minutes >= 0)
        val current = uiState.value
        if (!current.isDemo) return
        demoOffsetMillis += minutes * 60_000L
        val stopped = current.stoppedMillis + minutes * 60_000L
        update(current.copy(stoppedMillis = stopped,
            drivingMillis = if (stopped >= 600_000) 0 else current.drivingMillis,
            speedKph = 0, isDrowsy = false))
    }

    fun setDemoDuration(minutes: Int) {
        require(minutes >= 0)
        if (uiState.value.isDemo) update(uiState.value.copy(drivingMillis = minutes * 60_000L, stoppedMillis = 0))
    }

    fun setSpeed(kph: Int?) {
        require(kph == null || kph >= 0)
        if (uiState.value.isDemo) update(uiState.value.copy(speedKph = kph))
    }

    fun setDrowsy(drowsy: Boolean) {
        if (uiState.value.isDemo) update(uiState.value.copy(isDrowsy = drowsy))
    }

    /** 차량 속도 입력은 m/s를 제공하는 어댑터에서 km/h로 변환한다. */
    fun onVehicleSpeed(kph: Int?) {
        require(kph == null || kph >= 0)
        if (uiState.value.isDemo) return
        tick()
        lastSpeedSample = if (kph != null) clockMillis() else null
        update(uiState.value.copy(speedKph = kph))
    }

    fun tick() {
        val currentTime = clockMillis()
        val elapsed = (currentTime - lastTick).coerceAtLeast(0)
        val tickStart = lastTick
        lastTick = currentTime
        val current = uiState.value
        if (current.isDemo) {
            update(current) // 재알림은 수동 시간 이동 없이도 벽시계 기준으로 돌아온다.
            return
        }
        val sample = lastSpeedSample
        val validUntil = sample?.plus(SPEED_STALE_MILLIS)
        val validElapsed = if (validUntil == null) 0 else
            (minOf(currentTime, validUntil) - tickStart).coerceIn(0, elapsed)
        var next = current
        if (validElapsed > 0 && current.speedKph != null) {
            next = if (current.speedKph > 0) {
                current.copy(drivingMillis = current.drivingMillis + validElapsed, stoppedMillis = 0)
            } else {
                val stopped = current.stoppedMillis + validElapsed
                current.copy(stoppedMillis = stopped,
                    drivingMillis = if (stopped >= 600_000) 0 else current.drivingMillis)
            }
        }
        if (validUntil == null || currentTime > validUntil) {
            // 알 수 없는 시간은 정차로 간주하지 않는다.
            next = next.copy(speedKph = null, stoppedMillis = 0)
        }
        update(next)
    }

    fun nextWarning(): DrivingWarning? = DrivingWarning.entries.firstOrNull { warning ->
        val state = uiState.value
        val triggered = when (warning) {
            DrivingWarning.DROWSY -> state.isDrowsy
            DrivingWarning.SPEEDING -> (state.speedKph ?: 0) > DrivingState.SPEED_LIMIT_KPH
            DrivingWarning.REST -> state.drivingMillis >= 120 * 60_000L
        }
        val last = acknowledgements()[warning]
        triggered && (last == null || now() - last >= warning.cooldownMinutes * 60_000L)
    }

    fun acknowledge(warning: DrivingWarning) {
        acknowledgements()[warning] = now()
        update(uiState.value)
    }

    fun persistedDrivingMillis(): Long = if (uiState.value.isDemo) realState.drivingMillis else uiState.value.drivingMillis

    fun remainingRealCooldowns(): Map<DrivingWarning, Long> = DrivingWarning.entries.associateWith {
        val last = realAcknowledgedAt[it]
        if (last == null) 0 else (it.cooldownMinutes * 60_000L - (clockMillis() - last)).coerceAtLeast(0)
    }

    private fun acknowledgements() = if (uiState.value.isDemo) demoAcknowledgedAt else realAcknowledgedAt
    private fun now() = clockMillis() + if (uiState.value.isDemo) demoOffsetMillis else 0
    private fun update(state: DrivingState) {
        mutableState.value = state.copy(revision = mutableState.value.revision + 1)
        if (!state.isDemo) realState = mutableState.value
    }

    companion object { const val SPEED_STALE_MILLIS = 5_000L }
}
