package com.example.gbsw.shared.view

import androidx.car.app.CarContext
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.gbsw.shared.R
import com.example.gbsw.shared.model.*
import com.example.gbsw.shared.service.AppRuntime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalendarScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime), WarningResume {
    private var navigationError: String? = null
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { runtime.refreshCalendar() }
        })
    }
    override fun recreate() = CalendarScreen(carContext, runtime)
    override fun onGetTemplate(): Template = calendarTemplate(carContext, runtime, false, navigationError) {
        navigationError = NavigationDispatcher(runtime).calendar(it.location)
        if (navigationError != null) invalidate()
    }
}

internal fun calendarTime(event: CalendarEvent): String =
    Instant.ofEpochMilli(event.beginMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

internal fun calendarTemplate(
    context: CarContext, runtime: AppRuntime, embedded: Boolean, error: String? = null,
    navigate: (CalendarEvent) -> Unit,
): Template {
    val state = runtime.calendar()
    val event = state.data
    val row = Row.Builder().setTitle("다음 일정").setImage(carIcon(context, R.drawable.ic_calendar))
    if (state.status == LoadStatus.READY && event != null) {
        row.addText(error ?: "${calendarTime(event)} · ${event.title}").addText(event.location)
    } else {
        row.addText(state.message.ifBlank { "다음 일정을 확인하고 있어요" })
        row.addText(if (state.status == LoadStatus.PERMISSION) "권한을 거부해도 다른 기능은 사용할 수 있어요" else "오늘 일정 중 장소가 있는 다음 일정을 확인해요")
    }
    val pane = Pane.Builder().addRow(row.build()).apply {
        if (state.status == LoadStatus.READY && event != null) {
            addAction(primaryAction(context, if (error == null) "길안내" else "다시 시도", R.drawable.ic_pin) { navigate(event) })
        } else if (state.status == LoadStatus.PERMISSION) {
            addAction(Action.Builder().setTitle("캘린더 허용")
                .setOnClickListener(ParkedOnlyOnClickListener.create { runtime.requestCalendar() }).build())
        } else {
            addAction(Action.Builder().setTitle("다시 확인").setOnClickListener { runtime.refreshCalendar() }.build())
        }
    }.build()
    return PaneTemplate.Builder(pane).apply {
        if (!embedded) { setTitle("일정"); setHeaderAction(Action.BACK) }
    }.build()
}
