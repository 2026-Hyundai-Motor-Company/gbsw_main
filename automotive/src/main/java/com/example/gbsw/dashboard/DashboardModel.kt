package com.example.gbsw.dashboard

import android.Manifest
import android.app.Application
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gbsw.BuildConfig
import com.example.gbsw.shared.model.*
import com.example.gbsw.shared.service.DrivingPersistence
import com.example.gbsw.shared.service.WarningFeedback
import com.example.gbsw.shared.service.WarningNotifications
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DashboardTab(val label: String) { DRIVE("운전"), REST("휴게소"), SIGHTS("볼거리"), CALENDAR("일정") }

/** Native AAOS presentation reuses the tested domain store and Android data adapters. */
class DashboardModel(app: Application) : AndroidViewModel(app) {
    private val persistence = DrivingPersistence(app)
    val store = persistence.createStore(BuildConfig.DEBUG)
    val feedback = WarningFeedback(app)
    val location = AndroidLocationSource(app)
    private val calendarSource = AndroidCalendarRepository(app)
    private val placesSource = OverpassPlaceRepository()
    val driving = store.uiState
    private val changes = MutableStateFlow(0)
    val updates = changes.asStateFlow()
    private val realPlaces = mutableMapOf<PlaceCategory, FeatureState<List<Place>>>()
    private val jobs = mutableMapOf<PlaceCategory, Job>()
    private var calendarJob: Job? = null
    private var realCalendar = FeatureState<CalendarEvent>()
    var tab = DashboardTab.DRIVE
        private set
    var scenario = DemoScenario.NORMAL
        private set
    var position: GeoPoint? = null
        private set
    var navigationError: String? = null
        private set
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var observingCalendar = false
    private var foreground = false
    private val calendarObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refreshCalendar() }
    }
    private val speedListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            val speed = (value.value as? Float)?.takeIf { it.isFinite() && value.status == CarPropertyValue.STATUS_AVAILABLE }
            store.onVehicleSpeed(speed?.let { (abs(it) * 3.6f).roundToInt() })
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) { store.onVehicleSpeed(null) }
    }
    val notifications = WarningNotifications(app, store, feedback,
        DashboardActivity::class.java.name) { foreground && (tab == DashboardTab.REST || tab == DashboardTab.SIGHTS) }

    init {
        connectVehicle()
        refreshCalendar()
        viewModelScope.launch {
            driving.collect {
                notifications.update(store.nextWarning())
            }
        }
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000); store.tick(); seconds++
                if (seconds % 15 == 0) persistence.save(store)
                if (seconds % 60 == 0) refreshCalendar()
            }
        }
    }

    fun changed() { changes.value++ }
    fun setForeground(value: Boolean) { foreground = value; notifications.update(store.nextWarning()) }
    fun select(value: DashboardTab) {
        tab = value; navigationError = null
        when (value) {
            DashboardTab.REST -> refreshPlaces(PlaceCategory.REST)
            DashboardTab.SIGHTS -> refreshPlaces(PlaceCategory.SIGHT)
            DashboardTab.CALENDAR -> refreshCalendar()
            else -> Unit
        }
        changed()
    }
    fun visibleWarning(): DrivingWarning? = store.nextWarning().takeUnless {
        tab == DashboardTab.REST || tab == DashboardTab.SIGHTS
    }
    fun acknowledge(openRest: Boolean = false) {
        store.nextWarning()?.let(store::acknowledge)
        notifications.update(null)
        if (openRest) select(DashboardTab.REST)
        changed()
    }
    fun places(category: PlaceCategory): FeatureState<List<Place>> {
        if (driving.value.isDemo) return when (scenario) {
            DemoScenario.EMPTY_PLACES -> FeatureState(LoadStatus.EMPTY, emptyList())
            DemoScenario.LOCATION_ERROR -> FeatureState(LoadStatus.ERROR, message = "현재 위치를 확인하지 못했어요. 위치 설정을 확인한 후 다시 시도해주세요.")
            DemoScenario.NETWORK_ERROR -> FeatureState(LoadStatus.ERROR, message = "네트워크 연결을 확인한 후 다시 시도해주세요.")
            else -> FeatureState(LoadStatus.READY, demoPlaces(category), "시연용 장소 · 거리순")
        }
        if (!location.hasPermission()) return FeatureState(LoadStatus.PERMISSION, message = "위치를 허용하면 가까운 ${category.title}를 찾을 수 있어요.")
        return realPlaces[category] ?: FeatureState()
    }
    fun refreshPlaces(category: PlaceCategory) {
        if (driving.value.isDemo || !location.hasPermission()) { changed(); return }
        jobs[category]?.cancel()
        realPlaces[category] = FeatureState(LoadStatus.LOADING)
        changed()
        jobs[category] = viewModelScope.launch {
            realPlaces[category] = try {
                val origin = location.current(); position = origin
                val list = withTimeout(25_000) { placesSource.find(origin, category) }
                FeatureState(if (list.isEmpty()) LoadStatus.EMPTY else LoadStatus.READY, list)
            } catch (_: TimeoutCancellationException) {
                FeatureState(LoadStatus.ERROR, message = "응답이 늦어지고 있어요. 잠시 후 다시 시도해주세요.")
            } catch (e: CancellationException) { throw e
            } catch (_: SecurityException) {
                FeatureState(LoadStatus.PERMISSION, message = "위치 권한을 확인해주세요.")
            } catch (e: Exception) {
                FeatureState(LoadStatus.ERROR, message = if (e is IllegalStateException) e.message.orEmpty() else "장소를 불러오지 못했어요. 네트워크 연결 후 다시 시도해주세요.")
            }
            changed()
        }
    }
    fun calendar(): FeatureState<CalendarEvent> {
        if (driving.value.isDemo) return when (scenario) {
            DemoScenario.CALENDAR_DENIED -> FeatureState(LoadStatus.PERMISSION)
            DemoScenario.NO_EVENTS -> FeatureState(LoadStatus.EMPTY)
            else -> FeatureState(LoadStatus.READY, CalendarEvent(-1, "팀 미팅", demoEventTime(), "현대모터스튜디오 고양"))
        }
        if (!calendarSource.hasPermission()) return FeatureState(LoadStatus.PERMISSION)
        return realCalendar
    }
    fun refreshCalendar() {
        if (driving.value.isDemo || !calendarSource.hasPermission()) { changed(); return }
        if (!observingCalendar) {
            try {
                getApplication<Application>().contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver)
                observingCalendar = true
            } catch (_: SecurityException) { }
        }
        calendarJob?.cancel()
        realCalendar = realCalendar.copy(status = LoadStatus.LOADING); changed()
        calendarJob = viewModelScope.launch {
            realCalendar = try {
                val event = withTimeout(6_000) { calendarSource.nextEvent() }
                FeatureState(if (event == null) LoadStatus.EMPTY else LoadStatus.READY, event)
            } catch (_: TimeoutCancellationException) { FeatureState(LoadStatus.ERROR)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { FeatureState(LoadStatus.ERROR) }
            changed()
        }
    }
    fun permissionsChanged() { connectVehicle(); refreshCalendar(); if (tab == DashboardTab.REST) refreshPlaces(PlaceCategory.REST); if (tab == DashboardTab.SIGHTS) refreshPlaces(PlaceCategory.SIGHT); notifications.update(visibleWarning()); changed() }
    fun hasPermission(permission: String) = getApplication<Application>().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION", "MissingPermission")
    private fun connectVehicle() {
        properties?.unregisterCallback(speedListener); properties = null
        car?.disconnect(); car = null
        if (!hasPermission("android.car.permission.CAR_SPEED")) { store.onVehicleSpeed(null); return }
        try {
            car = Car.createCar(getApplication(), handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!ready) { properties = null; store.onVehicleSpeed(null); return@createCar }
                try {
                    properties = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    properties?.registerCallback(speedListener, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI)
                } catch (_: RuntimeException) { store.onVehicleSpeed(null) }
            }
        } catch (_: RuntimeException) { store.onVehicleSpeed(null) }
    }
    fun startDemo() {
        if (!BuildConfig.DEBUG) return
        jobs.values.forEach { it.cancel() }; calendarJob?.cancel()
        scenario = DemoScenario.NORMAL; store.startDemo(); navigationError = null; changed()
    }
    fun stopDemo() { store.stopDemo(); scenario = DemoScenario.NORMAL; navigationError = null; select(DashboardTab.DRIVE); permissionsChanged() }
    fun setScenario(value: DemoScenario) { if (driving.value.isDemo && BuildConfig.DEBUG) { scenario = value; changed() } }
    fun clearNavigationError() { navigationError = null; changed() }
    fun navigate(place: Place) = navigateUri(Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${URLEncoder.encode(place.name, "UTF-8")})"))
    fun navigate(event: CalendarEvent) = navigateUri(Uri.parse("geo:0,0?q=" + URLEncoder.encode(event.location, "UTF-8")))
    private fun navigateUri(uri: Uri) {
        if (driving.value.isDemo && scenario == DemoScenario.NAVIGATION_MISSING) {
            navigationError = "길안내 앱을 찾을 수 없습니다"; changed(); return
        }
        val app = getApplication<Application>()
        for (action in listOf("android.intent.action.NAVIGATE", Intent.ACTION_VIEW)) {
            val intent = Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(app.packageManager) == null) continue
            try { app.startActivity(intent); navigationError = null; changed(); return
            } catch (_: android.content.ActivityNotFoundException) { continue
            } catch (_: SecurityException) { break }
        }
        navigationError = "길안내 앱을 찾을 수 없습니다"; changed()
    }
    override fun onCleared() {
        persistence.save(store); properties?.unregisterCallback(speedListener); car?.disconnect()
        if (observingCalendar) getApplication<Application>().contentResolver.unregisterContentObserver(calendarObserver)
        feedback.close(); notifications.close()
    }
    private fun demoEventTime() = java.time.LocalDate.now().atTime(15, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    // Exact Figma presentation fixtures. Coordinates are examples; shown only in explicit debug simulation.
    private fun demoPlaces(category: PlaceCategory) = if (category == PlaceCategory.REST) listOf(
        Place("yeoju", "여주휴게소", 12.0, "", 37.2398, 127.5702),
        Place("icheon", "이천휴게소", 31.0, "", 37.269, 127.43),
        Place("deokpyeong", "덕평휴게소", 47.0, "", 37.2422, 127.3901),
    ) else listOf(
        Place("viewpoint", "남한강 전망대", 4.0, "전망", 37.296, 127.65),
        Place("ceramics", "여주 도자문화센터", 8.0, "문화", 37.29, 127.66),
        Place("silleuksa", "신륵사", 14.0, "명소", 37.2975, 127.6631),
    )
}
