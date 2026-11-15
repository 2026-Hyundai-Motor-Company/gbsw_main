package com.example.gbsw.shared.view

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.gbsw.shared.R
import com.example.gbsw.shared.model.*
import com.example.gbsw.shared.model.Place
import com.example.gbsw.shared.service.AppRuntime
import com.example.gbsw.shared.service.WarningSelectionScreen
import java.util.Locale

class PlacesScreen(context: CarContext, runtime: AppRuntime, private val category: PlaceCategory) : RuntimeScreen(context, runtime), WarningSelectionScreen {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { runtime.refreshPlaces(category) }
        })
    }
    override fun onGetTemplate(): Template = placeTemplate(carContext, runtime, category, false) {
        selectPlace(runtime, it)
    }
}

internal fun placeTemplate(
    context: CarContext, runtime: AppRuntime, category: PlaceCategory, embedded: Boolean,
    select: (Place) -> Unit,
): Template {
    val state = runtime.places(category)
    val data = state.data.orEmpty()
    val list = ItemList.Builder()
    if (state.status == LoadStatus.READY && data.isNotEmpty()) {
        data.take(6).forEach { place ->
            val distance = SpannableString("  · ${place.description}").apply {
                setSpan(DistanceSpan.create(Distance.create(place.distanceKm, Distance.UNIT_KILOMETERS)),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            list.addItem(Row.Builder().setTitle(place.name).addText(distance)
                .setMetadata(Metadata.Builder().setPlace(
                    androidx.car.app.model.Place.Builder(CarLocation.create(place.latitude, place.longitude))
                        .setMarker(PlaceMarker.Builder().build()).build()).build())
                .setOnClickListener { select(place) }.build())
        }
    } else {
        // 권한·오류·빈 결과·재시도에서도 타입과 상태 행 제목을 유지해 단계 한도를 지킨다.
        val retry = OnClickListener {
            if (state.status != LoadStatus.LOADING) runtime.refreshPlaces(category, true)
        }
        val listener = if (state.status == LoadStatus.PERMISSION)
            ParkedOnlyOnClickListener.create { runtime.requestLocation(category) } else retry
        list.addItem(Row.Builder().setTitle("주변 장소")
            .addText(state.message.ifBlank { "주변 장소를 불러오고 있어요" })
            .addText(when (state.status) {
                LoadStatus.PERMISSION -> "주차 후 눌러서 위치 허용 · 좌표만 장소 서버로 전송해요"
                LoadStatus.LOADING -> "잠시만 기다려주세요"
                else -> "눌러서 다시 시도"
            }).setBrowsable(true).setOnClickListener(listener).build())
    }
    if (!embedded && context.carAppApiLevel >= 2) {
        return PlaceListMapTemplate.Builder().setTitle(
            category.title + if (runtime.store.uiState.value.isDemo) " · 시연" else " · OSM 직선거리")
            .setHeaderAction(Action.BACK).setCurrentLocationEnabled(false).setItemList(list.build())
            .apply {
                runtime.lastPosition?.takeUnless { runtime.store.uiState.value.isDemo }?.let {
                    setAnchor(androidx.car.app.model.Place.Builder(CarLocation.create(it.latitude, it.longitude)).build())
                }
            }.build()
    }
    return ListTemplate.Builder().setSingleList(list.build()).apply {
        if (!embedded) { setTitle(category.title); setHeaderAction(Action.BACK) }
    }.build()
}

internal fun Screen.selectPlace(runtime: AppRuntime, place: Place) {
    val error = NavigationDispatcher(runtime).place(place)
    if (error != null) screenManager.push(PlaceDetailsScreen(carContext, runtime, place, error))
}

internal class PlaceDetailsScreen(context: CarContext, runtime: AppRuntime, private val place: Place,
    initialNavigationError: String? = null) : RuntimeScreen(context, runtime), WarningSelectionScreen {
    private var navigationError: String? = initialNavigationError
    override fun onGetTemplate(): Template {
        val demo = runtime.store.uiState.value.isDemo
        val distance = String.format(Locale.getDefault(), "%.1f km", place.distanceKm)
        return PaneTemplate.Builder(Pane.Builder()
            .addRow(Row.Builder().setTitle(place.name)
                .addText(navigationError ?: place.description)
                .addText(if (demo) "시연용 장소 · 예시 거리 $distance" else "직선거리 $distance · © OpenStreetMap contributors")
                .setImage(carIcon(carContext, R.drawable.ic_pin)).build())
            .addAction(primaryAction(carContext, if (navigationError == null) "길안내" else "다시 시도", R.drawable.ic_pin) {
                navigationError = NavigationDispatcher(runtime).place(place)
                if (navigationError != null) invalidate()
            }).build()).setTitle("장소 확인").setHeaderAction(Action.BACK).build()
    }
}

internal class InfoScreen(context: CarContext, runtime: AppRuntime, private val title: String, private val message: String) :
    RuntimeScreen(context, runtime), WarningResume {
    override fun recreate() = InfoScreen(carContext, runtime, title, message)
    override fun onGetTemplate(): Template = PaneTemplate.Builder(Pane.Builder()
        .addRow(Row.Builder().setTitle(title).addText(message).build())
        .addAction(Action.Builder().setTitle("확인").setOnClickListener { finish() }.build()).build())
        .setTitle(title).setHeaderAction(Action.BACK).build()
}
