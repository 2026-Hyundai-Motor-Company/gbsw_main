package com.example.gbsw.dashboard

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gbsw.BuildConfig
import com.example.gbsw.shared.model.DemoScenario

@Composable
fun SettingsPanel(model: DashboardModel, night: Boolean, request: (List<String>) -> Unit, onTheme: () -> Unit, onDemo: () -> Unit) {
    val driving by model.driving.collectAsState()
    DesignCard(Modifier.offset(48.dp, LocalDashboardLayout.current.contentTop.dp).size(1184.dp, LocalDashboardLayout.current.overlayHeight.dp), 28) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsRow("차량 속도", driving.speedKph?.let { "$it km/h · 차량 신호 연결됨" } ?: "연결하면 운전 시간과 속도를 확인할 수 있어요", "연결 확인") {
                if (model.hasPermission("android.car.permission.CAR_SPEED")) model.permissionsChanged()
                else request(listOf("android.car.permission.CAR_SPEED"))
            }
            SettingsRow("현재 위치", if (model.location.hasPermission()) "위치 연결됨" else "연결하면 가까운 휴게소와 볼거리를 찾아요", if (model.location.hasPermission()) "설정" else "위치 연결") {
                request(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
            SettingsRow("캘린더", if (model.hasPermission(Manifest.permission.READ_CALENDAR)) "연결됨 · 일정은 기기 안에서만 확인해요" else "연결하면 다음 일정의 목적지를 확인해요", if (model.hasPermission(Manifest.permission.READ_CALENDAR)) "설정" else "캘린더 연결") {
                request(listOf(Manifest.permission.READ_CALENDAR))
            }
            SettingsRow("운전 알림", if (model.notifications.hasPermission()) "휴식·위험 알림 켜짐" else "연결하면 쉬어갈 시점을 알려드려요", if (model.notifications.hasPermission()) "설정" else "알림 켜기") { request(listOf(Manifest.permission.POST_NOTIFICATIONS)) }
            SettingsRow("화면", if (night) "야간 모드" else "주간 모드", if (night) "주간" else "야간", onTheme)
            SettingsRow("경고음", if (model.feedback.soundEnabled) "짧고 낮은 음량의 경고음 켜짐" else "꺼짐", "전환") { model.feedback.soundEnabled = !model.feedback.soundEnabled; model.changed() }
            SettingsRow("귀신 아이콘", if (model.feedback.ghostEnabled) "위험 경고에 표시" else "기본 알림 아이콘으로 표시", "전환") { model.feedback.ghostEnabled = !model.feedback.ghostEnabled; model.changed() }
            if (BuildConfig.DEBUG) {
                SettingsRow("디자인 시연", "Figma와 같은 예시 데이터로 확인해요 · 실제 기록과 분리", if (driving.isDemo) "종료" else "시작") {
                    if (model.driving.value.isDemo) model.stopDemo() else model.startDemo()
                }
                SettingsRow("개발용 시연 도구", "시간·속도·졸음·정차·조회 실패", "열기", onDemo)
            }
            Copy("실제 졸음 센서와 계기판 제어는 제조사 권한이 필요해요.", 20, color = LocalDesign.current.secondary, modifier = Modifier.padding(top = 24.dp))
        }
    }
}

@Composable
private fun SettingsRow(title: String, description: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Copy(title, 28, true); Copy(description, 22, color = LocalDesign.current.secondary, lines = 2) }
        QuietButton(action, Modifier.width(if (LocalDashboardLayout.current.largeText) 320.dp else 144.dp), onClick)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDesign.current.border))
}

@Composable
fun DemoPanel(model: DashboardModel, close: () -> Unit) {
    if (!BuildConfig.DEBUG) return
    Column(Modifier.offset(48.dp, LocalDashboardLayout.current.contentTop.dp).size(1184.dp, LocalDashboardLayout.current.overlayHeight.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fun prepare() { model.startDemo(); model.select(DashboardTab.DRIVE) }
        DemoRow("연속 운전 2시간", "휴식 권고 화면 재현") { prepare(); model.store.setDemoDuration(120); close() }
        DemoRow("졸음 상태 켜기", "졸음·귀신 경고 재현") { prepare(); model.store.setDrowsy(true); close() }
        DemoRow("속도 101 km/h", "시연 기준 100 km/h 초과 재현") { prepare(); model.store.setSpeed(101); close() }
        DemoRow("운전 30분", "운전 시간과 휴식 기준 확인") { prepare(); model.store.setDemoDuration(30); close() }
        DemoRow("9분 정차", "연속 운전 시간 유지") { if (!model.driving.value.isDemo) prepare(); model.store.stopFor(9); close() }
        DemoRow("1분 더 정차", "연속 정차 10분 도달 시 초기화") { if (!model.driving.value.isDemo) prepare(); model.store.stopFor(1); close() }
        DemoRow("속도 정보 없음", "차량 신호가 없으면 측정과 과속 판정을 멈춰요") { prepare(); model.store.setSpeed(null); close() }
        listOf("장소 없음" to DemoScenario.EMPTY_PLACES, "위치 확인 오류" to DemoScenario.LOCATION_ERROR,
            "네트워크 오류" to DemoScenario.NETWORK_ERROR, "길안내 앱 없음" to DemoScenario.NAVIGATION_MISSING,
            "캘린더 권한 거부" to DemoScenario.CALENDAR_DENIED, "일정 없음" to DemoScenario.NO_EVENTS).forEach { (title, scenario) ->
            DemoRow(title, "시연 상태 · 실제 기기 설정은 바꾸지 않아요") {
                prepare(); model.setScenario(scenario)
                model.select(if (scenario == DemoScenario.CALENDAR_DENIED || scenario == DemoScenario.NO_EVENTS) DashboardTab.CALENDAR else DashboardTab.REST)
                close()
            }
        }
        DemoRow("시연 종료", "실제 운전 기록으로 돌아가기") { model.stopDemo(); close() }
    }
}

@Composable
private fun DemoRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 144.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            FigmaIcon("150-3324/imgIconAlert.svg", 48, tint = LocalDesign.current.text)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Copy(title, 32, true); Copy(subtitle, 24, color = LocalDesign.current.secondary, lines = 2)
            }
            FigmaIcon("150-3324/imgIconNext.svg", 32, tint = LocalDesign.current.secondary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDesign.current.border))
    }
}
