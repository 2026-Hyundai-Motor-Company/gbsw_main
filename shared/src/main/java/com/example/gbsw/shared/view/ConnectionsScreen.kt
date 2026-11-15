package com.example.gbsw.shared.view

import androidx.car.app.CarContext
import androidx.car.app.model.*
import com.example.gbsw.shared.service.AppRuntime

internal class ConnectionsScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime), WarningResume {
    override fun recreate() = ConnectionsScreen(carContext, runtime)
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(Row.Builder().setTitle("차량 속도")
                .addText(if (runtime.vehicleSource.hasPermission()) "권한 허용됨 · 현재 속도 ${runtime.store.uiState.value.speedKph?.let { "$it km/h" } ?: "정보 없음"}" else "주차 후 눌러서 차량 속도 권한 허용")
                .setOnClickListener(ParkedOnlyOnClickListener.create { runtime.requestVehicle() }).build())
            .addItem(Row.Builder().setTitle("현재 위치")
                .addText(if (runtime.locationSource.hasPermission()) "권한 허용됨 · 장소 조회에는 좌표만 전송해요" else "휴게소와 볼거리 안내에 사용해요")
                .setOnClickListener(ParkedOnlyOnClickListener.create { runtime.requestLocation(null) }).build())
            .addItem(Row.Builder().setTitle("운전 알림")
                .addText(if (runtime.notifications.hasPermission()) "휴식·위험 경고 알림 켜짐" else "앱을 보고 있지 않을 때도 경고를 받아요")
                .setOnClickListener(ParkedOnlyOnClickListener.create { runtime.requestNotifications() }).build())
            .addItem(Row.Builder().setTitle("캘린더")
                .addText(if (runtime.calendarSource.hasPermission()) "권한 허용됨 · 일정은 기기 안에서만 조회해요" else "장소가 있는 다음 일정을 확인해요")
                .setOnClickListener(ParkedOnlyOnClickListener.create { runtime.requestCalendar() }).build())
            .addItem(Row.Builder().setTitle("경고 연출").addText("경고음 · 귀신 아이콘").setBrowsable(true)
                .setOnClickListener { screenManager.push(WarningSettingsScreen(carContext, runtime)) }.build())
            .addItem(Row.Builder().setTitle("차량 기능 지원").addText("졸음 감지 · 계기판 · 데이터 출처").setBrowsable(true)
                .setOnClickListener { screenManager.push(InfoScreen(carContext, runtime, "차량 기능 지원",
                    "속도는 차량이 제공하는 경우에만 측정합니다. 실제 졸음 센서와 계기판 제어는 제조사 권한이 필요하여 앱 내 경고와 시연으로 대체합니다. 장소: © OpenStreetMap contributors, 직선거리·진행 방향 기준. 실제 도로 접근은 길안내 앱에서 확인하세요.")) }.build())
            .build()
        return ListTemplate.Builder().setTitle("차량 연결과 설정").setHeaderAction(Action.BACK).setSingleList(list).build()
    }
}

private class WarningSettingsScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime), WarningResume {
    override fun recreate() = WarningSettingsScreen(carContext, runtime)
    override fun onGetTemplate(): Template = ListTemplate.Builder().setTitle("경고 연출").setHeaderAction(Action.BACK)
        .setSingleList(ItemList.Builder()
            .addItem(Row.Builder().setTitle("경고음").addText("낮은 음량의 짧은 알림 · 기기 무음 설정 유지")
                .setToggle(Toggle.Builder { checked -> runtime.feedback.soundEnabled = checked; runtime.changed() }
                    .setChecked(runtime.feedback.soundEnabled).build()).build())
            .addItem(Row.Builder().setTitle("귀신 아이콘").addText("경고 옆에 작은 아이콘 표시 · 섬광 없음")
                .setToggle(Toggle.Builder { checked -> runtime.feedback.ghostEnabled = checked; runtime.changed() }
                    .setChecked(runtime.feedback.ghostEnabled).build()).build())
            .build()).build()
}
