package com.example.gbsw.shared.service

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.car.app.CarContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.gbsw.shared.BuildConfig
import com.example.gbsw.shared.model.*
import com.example.gbsw.shared.viewmodel.DrivingSessionStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** 한 차량 Session에 속하는 입력·조회·설정 조정자. 화면은 이 상태를 관찰한다. */
class AppRuntime(val context: CarContext, lifecycle: Lifecycle) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistence = DrivingPersistence(context)
    val store: DrivingSessionStore = persistence.createStore(BuildConfig.DEBUG)
    val feedback = WarningFeedback(context)
    val notifications = WarningNotifications(context, store, feedback)
    val locationSource = AndroidLocationSource(context)
    val calendarSource = AndroidCalendarRepository(context)
    val vehicleSource = VehicleSpeedSource(context, store::onVehicleSpeed)
    private val placeSource = OverpassPlaceRepository()
    private val mutableUpdates = MutableStateFlow(0L)
    val updates = mutableUpdates.asStateFlow()
    private val placeStates = mutableMapOf<PlaceCategory, FeatureState<List<Place>>>()
    private val placeJobs = mutableMapOf<PlaceCategory, Job>()
    private val placeLoadedAt = mutableMapOf<PlaceCategory, Long>()
    private var calendarState = FeatureState<CalendarEvent>()
    private var calendarJob: Job? = null
    var scenario: DemoScenario = DemoScenario.NORMAL
        private set
    var lastPosition: GeoPoint? = null
        private set
    var resumeAfterWarning: (() -> Unit)? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshCalendar() }
    }
    private var observingCalendar = false

    init {
        lifecycle.addObserver(this)
        scope.launch {
            store.uiState.map { state ->
                listOf(state.isDemo, state.drivingMinutes, state.stoppedMinutes,
                    state.speedKph, state.isDrowsy, store.nextWarning())
            }.distinctUntilChanged().collect { changed() }
        }
        scope.launch { store.uiState.collect { notifications.update(store.nextWarning()) } }
        vehicleSource.start()
        if (calendarSource.hasPermission()) refreshCalendar()
        scope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                store.tick()
                seconds++
                if (seconds % 15 == 0) persistence.save(store)
                if (seconds % 60 == 0 && calendarSource.hasPermission()) refreshCalendar()
            }
        }
    }

    fun changed() { mutableUpdates.value++ }

    fun places(category: PlaceCategory): FeatureState<List<Place>> {
        if (store.uiState.value.isDemo) return when (scenario) {
            DemoScenario.EMPTY_PLACES -> FeatureState(LoadStatus.EMPTY, emptyList(), "근처에 ${category.title}가 없습니다")
            DemoScenario.LOCATION_ERROR -> FeatureState(LoadStatus.ERROR, message = "현재 위치를 확인하지 못했어요. 다시 시도해주세요")
            DemoScenario.NETWORK_ERROR -> FeatureState(LoadStatus.ERROR, message = "장소를 불러오지 못했어요. 네트워크 연결 후 다시 시도해주세요")
            else -> FeatureState(LoadStatus.READY, store.places(category), "시연용 장소와 거리예요")
        }
        if (!locationSource.hasPermission()) return FeatureState(LoadStatus.PERMISSION, message = "위치를 허용하면 가까운 ${category.title}를 안내할 수 있어요")
        return placeStates[category] ?: FeatureState(LoadStatus.IDLE, message = "주변 장소를 확인해주세요")
    }

    fun calendar(): FeatureState<CalendarEvent> {
        if (store.uiState.value.isDemo) return when (scenario) {
            DemoScenario.CALENDAR_DENIED -> FeatureState(LoadStatus.PERMISSION, message = "캘린더 권한이 꺼져 있어요")
            DemoScenario.NO_EVENTS -> FeatureState(LoadStatus.EMPTY, message = "장소가 있는 일정이 없습니다")
            else -> FeatureState(LoadStatus.READY,
                CalendarEvent(-1, "팀 미팅 · 시연", System.currentTimeMillis() + 600_000, "현대모터스튜디오 고양"))
        }
        if (!calendarSource.hasPermission()) return FeatureState(LoadStatus.PERMISSION, message = "장소가 있는 다음 일정을 확인하려면 캘린더를 허용해주세요")
        return calendarState
    }

    fun refreshPlaces(category: PlaceCategory, force: Boolean = false) {
        if (store.uiState.value.isDemo) { changed(); return }
        if (!locationSource.hasPermission()) { changed(); return }
        if (placeJobs[category]?.isActive == true) return
        if (!force && placeStates[category]?.status == LoadStatus.READY &&
            System.currentTimeMillis() - (placeLoadedAt[category] ?: 0) < 60_000) return
        placeStates[category] = FeatureState(LoadStatus.LOADING, message = "위치와 주변 장소를 불러오고 있어요")
        changed()
        placeJobs[category] = scope.launch {
            try {
                val position = locationSource.current()
                lastPosition = position
                val result = withTimeout(25_000) { placeSource.find(position, category) }
                placeStates[category] = if (result.isEmpty())
                    FeatureState(LoadStatus.EMPTY, emptyList(), "근처에 ${category.title}가 없습니다")
                else FeatureState(LoadStatus.READY, result, "© OpenStreetMap contributors · 직선거리")
                placeLoadedAt[category] = System.currentTimeMillis()
            } catch (error: TimeoutCancellationException) {
                placeStates[category] = FeatureState(LoadStatus.ERROR, message = "응답이 늦어지고 있어요. 잠시 후 다시 시도해주세요")
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                placeStates[category] = FeatureState(LoadStatus.PERMISSION, message = "위치 권한을 확인해주세요")
            } catch (error: Exception) {
                placeStates[category] = FeatureState(LoadStatus.ERROR, message =
                    if (error is IllegalStateException) error.message.orEmpty()
                    else "장소를 불러오지 못했어요. 네트워크 연결 후 다시 시도해주세요")
            }
            changed()
        }
    }

    fun refreshCalendar() {
        if (store.uiState.value.isDemo) { changed(); return }
        if (!calendarSource.hasPermission()) { changed(); return }
        observeCalendar()
        calendarJob?.cancel()
        calendarState = FeatureState(LoadStatus.LOADING, message = "다음 일정을 확인하고 있어요")
        changed()
        calendarJob = scope.launch {
            try {
                val event = withTimeout(6_000) { calendarSource.nextEvent() }
                calendarState = if (event == null) FeatureState(LoadStatus.EMPTY, message = "장소가 있는 일정이 없습니다")
                else FeatureState(LoadStatus.READY, event)
            } catch (_: TimeoutCancellationException) {
                calendarState = FeatureState(LoadStatus.ERROR, message = "일정 조회가 늦어지고 있어요. 다시 시도해주세요")
            } catch (error: CancellationException) { throw error
            } catch (_: SecurityException) {
                calendarState = FeatureState(LoadStatus.PERMISSION, message = "캘린더 권한을 확인해주세요")
            } catch (_: Exception) {
                calendarState = FeatureState(LoadStatus.ERROR, message = "기기의 캘린더를 읽을 수 없어요. 다시 시도해주세요")
            }
            changed()
        }
    }

    fun requestLocation(category: PlaceCategory?) {
        request(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (category != null) refreshPlaces(category, true) else changed()
        }
    }
    fun requestCalendar() {
        if (store.uiState.value.isDemo) { scenario = DemoScenario.NORMAL; changed(); return }
        request(listOf(Manifest.permission.READ_CALENDAR)) { refreshCalendar(); changed() }
    }
    fun requestVehicle() { request(listOf(VehicleSpeedSource.PERMISSION, Manifest.permission.POST_NOTIFICATIONS)) { vehicleSource.start(); changed() } }
    fun requestNotifications() { request(listOf(Manifest.permission.POST_NOTIFICATIONS)) { notifications.update(store.nextWarning()); changed() } }

    private fun request(permissions: List<String>, done: () -> Unit) {
        try {
            context.requestPermissions(permissions, context.mainExecutor) { _, _ -> done() }
        } catch (_: RuntimeException) { done() }
    }

    fun setScenario(value: DemoScenario) {
        if (!store.demoAvailable || !store.uiState.value.isDemo) return
        scenario = value
        changed()
    }

    fun startDemo() {
        scenario = DemoScenario.NORMAL
        placeJobs.values.forEach { it.cancel() }
        calendarJob?.cancel()
        store.startDemo()
    }

    fun stopDemo() {
        store.stopDemo()
        scenario = DemoScenario.NORMAL
        placeStates.clear()
        vehicleSource.start()
        if (calendarSource.hasPermission()) refreshCalendar()
        changed()
    }

    fun acknowledgeWarning(openPlaces: Boolean = false) {
        store.nextWarning()?.let(store::acknowledge)
        feedback.present(null)
        notifications.update(null)
        val resume = resumeAfterWarning
        resumeAfterWarning = null
        changed()
        if (!openPlaces) resume?.invoke()
    }

    private fun observeCalendar() {
        if (observingCalendar) return
        try {
            context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
            observingCalendar = true
        } catch (_: RuntimeException) { }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        persistence.save(store)
        vehicleSource.stop()
        notifications.close()
        feedback.close()
        if (observingCalendar) context.contentResolver.unregisterContentObserver(observer)
        scope.cancel()
    }
}
