package com.example.gbsw.shared.model

enum class LoadStatus { IDLE, PERMISSION, LOADING, READY, EMPTY, ERROR }
data class FeatureState<T>(
    val status: LoadStatus = LoadStatus.IDLE,
    val data: T? = null,
    val message: String = "",
)
data class GeoPoint(val latitude: Double, val longitude: Double, val bearing: Double? = null) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(bearing == null || bearing.isFinite())
    }
}
data class CalendarEvent(val id: Long, val title: String, val beginMillis: Long, val location: String)
enum class DemoScenario {
    NORMAL, EMPTY_PLACES, LOCATION_ERROR, NETWORK_ERROR, NAVIGATION_MISSING, CALENDAR_DENIED, NO_EVENTS
}
