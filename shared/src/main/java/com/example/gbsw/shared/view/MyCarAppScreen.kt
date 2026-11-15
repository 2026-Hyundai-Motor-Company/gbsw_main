package com.example.gbsw.shared.view

import android.text.SpannableString
import android.text.Spanned
import androidx.activity.OnBackPressedCallback
import androidx.car.app.CarContext
import androidx.car.app.model.*
import com.example.gbsw.shared.R
import com.example.gbsw.shared.model.*
import com.example.gbsw.shared.service.AppRuntime
import com.example.gbsw.shared.service.WarningSelectionScreen

class MyCarAppScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime), WarningSelectionScreen {
    private var selectedTab = HomeTab.DRIVE
    private var calendarError: String? = null
    override val isChoosingPlace: Boolean get() = selectedTab == HomeTab.REST || selectedTab == HomeTab.SIGHTS
    private val warningBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (runtime.store.nextWarning() != null) runtime.acknowledgeWarning()
            else { isEnabled = false; carContext.onBackPressedDispatcher.onBackPressed() }
        }
    }

    init { carContext.onBackPressedDispatcher.addCallback(this, warningBack) }

    override fun onGetTemplate(): Template {
        warningBack.isEnabled = runtime.store.nextWarning() != null && !isChoosingPlace
        if (runtime.store.nextWarning() != null && !isChoosingPlace && selectedTab != HomeTab.DRIVE) {
            val origin = selectedTab
            runtime.resumeAfterWarning = { selectedTab = origin; runtime.changed() }
            selectedTab = HomeTab.DRIVE
        }
        if (carContext.carAppApiLevel < 6) return homeTemplate(false)
        val content = when (selectedTab) {
            HomeTab.DRIVE -> homeTemplate(true)
            HomeTab.REST -> placeTemplate(carContext, runtime, PlaceCategory.REST, true) {
                selectPlace(runtime, it)
            }
            HomeTab.SIGHTS -> placeTemplate(carContext, runtime, PlaceCategory.SIGHT, true) {
                selectPlace(runtime, it)
            }
            HomeTab.CALENDAR -> calendarTemplate(carContext, runtime, true, calendarError) {
                calendarError = NavigationDispatcher(runtime).calendar(it.location)
                if (calendarError != null) invalidate()
            }
        }
        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                if (selectedTab == HomeTab.DRIVE) runtime.acknowledgeWarning(openPlaces = true)
                selectedTab = HomeTab.valueOf(tabContentId)
                when (selectedTab) {
                    HomeTab.REST -> runtime.refreshPlaces(PlaceCategory.REST)
                    HomeTab.SIGHTS -> runtime.refreshPlaces(PlaceCategory.SIGHT)
                    HomeTab.CALENDAR -> runtime.refreshCalendar()
                    else -> Unit
                }
                invalidate()
            }
        }).setHeaderAction(Action.APP_ICON).setActiveTabContentId(selectedTab.name)
            .setTabContents(TabContents.Builder(content).build()).apply {
                HomeTab.entries.forEach { tab ->
                    addTab(Tab.Builder().setContentId(tab.name).setTitle(tab.title)
                        .setIcon(carIcon(carContext, tab.icon)).build())
                }
            }.build()
    }

    private fun homeTemplate(embedded: Boolean): PaneTemplate {
        val state = runtime.store.uiState.value
        val warning = runtime.store.nextWarning()
        val placeState = runtime.places(PlaceCategory.REST)
        val nearest = placeState.data?.firstOrNull()
        val eventState = runtime.calendar()
        val event = eventState.data
        // 제목 문자열과 행 수 고정 + DurationSpan: 시간·경고 갱신이 탐색 단계를 소모하지 않는다.
        val durationTitle = SpannableString("연속 운전 ·  ").apply {
            setSpan(DurationSpan.create(state.drivingMinutes * 60L), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val statusPrefix = if (state.isDemo) "시연 · " else ""
        val statusImage = if (warning != null && runtime.feedback.ghostEnabled) R.drawable.ic_ghost else R.drawable.ic_car
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(durationTitle)
                .addText(warning?.let { statusPrefix + it.title } ?: state.restContext)
                .addText(warning?.message ?: statusPrefix + (state.speedKph?.let { "$it km/h · 기준 100 km/h" } ?: "차량 연결 메뉴에서 속도 권한을 확인해주세요"))
                .setImage(carIcon(carContext, statusImage)).build())
            .addRow(Row.Builder().setTitle("가까운 휴게소")
                .addText(nearest?.let {
                    "${it.name} · ${if (state.isDemo) "시연" else "직선"} ${"%.1f".format(it.distanceKm)} km"
                } ?: placeState.message.ifBlank { "휴게소 찾기에서 현재 위치를 확인해주세요" })
                .setImage(carIcon(carContext, R.drawable.ic_coffee)).build())
            .addRow(Row.Builder().setTitle("다음 일정")
                .addText(event?.let { "${calendarTime(it)} · ${it.title}" }
                    ?: eventState.message.ifBlank { "일정 메뉴에서 다음 목적지를 확인해주세요" })
                .addText(event?.location ?: "장소가 있는 오늘의 다음 일정")
                .setImage(carIcon(carContext, R.drawable.ic_calendar)).build())
            .addAction(primaryAction(carContext, "휴게소 찾기", R.drawable.ic_coffee) {
                runtime.acknowledgeWarning(openPlaces = true)
                screenManager.push(PlacesScreen(carContext, runtime, PlaceCategory.REST))
            })
            .addAction(Action.Builder().setTitle(if (warning != null) "확인" else "더보기").setOnClickListener {
                if (warning != null) runtime.acknowledgeWarning()
                else screenManager.push(MoreScreen(carContext, runtime))
            }.build()).build()
        return PaneTemplate.Builder(pane).apply {
            if (!embedded) { setTitle("운전"); setHeaderAction(Action.APP_ICON) }
        }.build()
    }
}

private enum class HomeTab(val title: String, val icon: Int) {
    DRIVE("운전", R.drawable.ic_car), REST("휴게소", R.drawable.ic_coffee),
    SIGHTS("볼거리", R.drawable.ic_pin), CALENDAR("일정", R.drawable.ic_calendar),
}

internal class MoreScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime), WarningResume {
    override fun recreate() = MoreScreen(carContext, runtime)
    override fun onGetTemplate(): Template = ListTemplate.Builder().setTitle("더보기").setHeaderAction(Action.BACK)
        .setSingleList(ItemList.Builder()
            .addItem(Row.Builder().setTitle("볼거리").setBrowsable(true)
                .setOnClickListener { screenManager.push(PlacesScreen(carContext, runtime, PlaceCategory.SIGHT)) }.build())
            .addItem(Row.Builder().setTitle("일정").setBrowsable(true)
                .setOnClickListener { screenManager.push(CalendarScreen(carContext, runtime)) }.build())
            .addItem(Row.Builder().setTitle("차량 연결과 설정").setBrowsable(true)
                .setOnClickListener { screenManager.push(ConnectionsScreen(carContext, runtime)) }.build())
            .apply {
                if (runtime.store.demoAvailable) addItem(Row.Builder().setTitle("개발용 시연")
                    .addText("운전 시간 · 속도 · 졸음 · 실패 상태")
                    .setBrowsable(true).setOnClickListener(ParkedOnlyOnClickListener.create { screenManager.push(DemoScreen(carContext, runtime)) }).build())
            }.build()).build()
}
