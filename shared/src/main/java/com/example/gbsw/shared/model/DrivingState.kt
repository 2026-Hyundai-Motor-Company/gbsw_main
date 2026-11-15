package com.example.gbsw.shared.model

data class DrivingState(
    val drivingMillis: Long = 0,
    val stoppedMillis: Long = 0,
    val speedKph: Int? = null,
    val isDrowsy: Boolean = false,
    val isDemo: Boolean = false,
    val revision: Long = 0,
) {
    val drivingMinutes: Int get() = (drivingMillis / 60_000).toInt()
    val stoppedMinutes: Int get() = (stoppedMillis / 60_000).toInt()
    val durationText: String get() = when {
        drivingMinutes < 60 -> "${drivingMinutes}분"
        drivingMinutes % 60 == 0 -> "${drivingMinutes / 60}시간"
        else -> "${drivingMinutes / 60}시간 ${drivingMinutes % 60}분"
    }
    val restContext: String get() = when {
        speedKph == null -> "속도 정보 없음 · 운전 시간 측정을 잠시 멈췄어요"
        stoppedMinutes >= STOP_RESET_MINUTES -> "충분히 쉬었어요. 안전하게 출발하세요"
        drivingMinutes >= REST_INTERVAL_MINUTES -> "잠시 쉬어갈 시간이에요"
        drivingMinutes == 0 -> "안전하게 출발하세요"
        else -> "휴식 알림까지 ${REST_INTERVAL_MINUTES - drivingMinutes}분"
    }
    companion object {
        const val REST_INTERVAL_MINUTES = 120
        const val STOP_RESET_MINUTES = 10
        const val SPEED_LIMIT_KPH = 100
    }
}

enum class DrivingWarning(val title: String, val message: String, val cooldownMinutes: Int) {
    DROWSY("잠시 쉬어가세요", "졸음이 감지됐어요. 가까운 휴게소에서 쉬어가세요.", 5),
    SPEEDING("속도를 줄여주세요", "기준 속도 100 km/h를 넘었어요. 안전한 속도를 유지하세요.", 5),
    REST("이제 쉬어갈 시간이에요", "연속 운전 2시간이 지났어요. 가까운 휴게소를 확인하세요.", 30),
}
