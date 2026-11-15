package com.example.gbsw.shared.view

import androidx.car.app.CarContext
import androidx.car.app.model.*
import com.example.gbsw.shared.model.*
import com.example.gbsw.shared.service.AppRuntime

/** 디버그 진입만 존재하며 실제 기록을 덮어쓰지 않는다. */
internal class DemoScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime) {
    override fun onGetTemplate(): Template {
        check(runtime.store.demoAvailable)
        val items = ItemList.Builder()
        fun action(title: String, detail: String, block: () -> Unit) {
            items.addItem(Row.Builder().setTitle(title).addText(detail).setOnClickListener {
                block(); screenManager.popToRoot()
            }.build())
        }
        fun next(title: String, detail: String, screen: () -> androidx.car.app.Screen) {
            items.addItem(Row.Builder().setTitle(title).addText(detail).setBrowsable(true)
                .setOnClickListener { screenManager.push(screen()) }.build())
        }
        if (!runtime.store.uiState.value.isDemo) {
            action("시연 시작", "1시간 12분 · 85 km/h · 예시 장소와 일정") { runtime.startDemo() }
            action("0분부터 시작", "30분 운전과 10분 휴식 기준 확인") {
                runtime.startDemo(); runtime.store.setDemoDuration(0)
            }
        } else {
            next("운전 시간", runtime.store.uiState.value.durationText) { DemoValuesScreen(carContext, runtime, DemoValue.TIME) }
            next("속도", runtime.store.uiState.value.speedKph?.let { "$it km/h" } ?: "속도 정보 없음") {
                DemoValuesScreen(carContext, runtime, DemoValue.SPEED)
            }
            action(if (runtime.store.uiState.value.isDrowsy) "졸음 해제" else "졸음 경고", "판정과 휴게소 안내 흐름 확인") {
                runtime.store.setDrowsy(!runtime.store.uiState.value.isDrowsy)
            }
            next("정차와 휴식", "9분 유지 · 10분 초기화") { DemoValuesScreen(carContext, runtime, DemoValue.STOP) }
            next("조회 실패 상태", runtime.scenario.name) { DemoScenarioScreen(carContext, runtime) }
            action("시연 종료", "실제 운전 기록과 차량 입력으로 돌아가기") { runtime.stopDemo() }
        }
        return ListTemplate.Builder().setTitle("개발용 시연").setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}

private enum class DemoValue(val title: String, val unit: String) {
    TIME("운전 시간", "분"), SPEED("속도", "km/h"), STOP("정차 시간 추가", "분")
}

private class DemoValuesScreen(context: CarContext, runtime: AppRuntime, private val value: DemoValue) : RuntimeScreen(context, runtime) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        fun item(title: String, block: () -> Unit) {
            items.addItem(Row.Builder().setTitle(title).setOnClickListener {
                block(); screenManager.popToRoot()
            }.build())
        }
        when (value) {
            DemoValue.TIME -> {
                item("30분으로 설정") { runtime.store.setDemoDuration(30) }
                item("2시간으로 설정") { runtime.store.setDemoDuration(120) }
                item("30분 추가") { runtime.store.driveFor(30) }
            }
            DemoValue.SPEED -> {
                item("100 km/h · 정상 경계") { runtime.store.setSpeed(100) }
                item("101 km/h · 과속") { runtime.store.setSpeed(101) }
                item("속도 정보 없음") { runtime.store.setSpeed(null) }
            }
            DemoValue.STOP -> {
                item("9분 정차") { runtime.store.stopFor(9) }
                item("1분 더 정차") { runtime.store.stopFor(1) }
                item("10분 정차") { runtime.store.stopFor(10) }
            }
        }
        items.addItem(Row.Builder().setTitle("직접 조정").setBrowsable(true)
            .setOnClickListener { screenManager.push(DemoNumberScreen(carContext, runtime, value)) }.build())
        return ListTemplate.Builder().setTitle(value.title).setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}

/** 5번째 단계는 종료 가능한 Pane으로 제공한다. 뒤로 가기는 취소, 적용은 홈으로 복귀한다. */
private class DemoNumberScreen(context: CarContext, runtime: AppRuntime, private val type: DemoValue) : RuntimeScreen(context, runtime) {
    private var value = if (type == DemoValue.SPEED) 100 else 0
    override fun onGetTemplate(): Template {
        val pane = Pane.Builder().addRow(Row.Builder().setTitle("새 값")
            .addText("$value ${type.unit}").addText("상단 적용을 누르면 반영해요. 뒤로 가기는 취소예요.").build())
            .addAction(Action.Builder().setTitle(if (type == DemoValue.SPEED) "−1" else "+1")
                .setOnClickListener { value = (value + if (type == DemoValue.SPEED) -1 else 1).coerceIn(0, 999); invalidate() }.build())
            .addAction(Action.Builder().setTitle(if (type == DemoValue.SPEED) "+1" else "+10")
                .setOnClickListener { value = (value + if (type == DemoValue.SPEED) 1 else 10).coerceIn(0, 999); invalidate() }.build())
            .build()
        return PaneTemplate.Builder(pane).setTitle(type.title).setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder().addAction(Action.Builder().setTitle("적용").setOnClickListener {
                when (type) {
                    DemoValue.TIME -> runtime.store.setDemoDuration(value)
                    DemoValue.SPEED -> runtime.store.setSpeed(value)
                    DemoValue.STOP -> runtime.store.stopFor(value)
                }
                screenManager.popToRoot()
            }.build()).build()).build()
    }
}

private class DemoScenarioScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        listOf("정상 조회" to DemoScenario.NORMAL, "장소 없음" to DemoScenario.EMPTY_PLACES,
            "위치 오류" to DemoScenario.LOCATION_ERROR, "네트워크 오류" to DemoScenario.NETWORK_ERROR,
            "길안내 앱 없음" to DemoScenario.NAVIGATION_MISSING).forEach { (title, scenario) ->
            items.addItem(Row.Builder().setTitle(title).setOnClickListener {
                runtime.setScenario(scenario); screenManager.popToRoot()
            }.build())
        }
        items.addItem(Row.Builder().setTitle("캘린더 상태").setBrowsable(true).setOnClickListener {
            screenManager.push(DemoCalendarStateScreen(carContext, runtime))
        }.build())
        return ListTemplate.Builder().setTitle("조회 실패 상태").setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}

private class DemoCalendarStateScreen(context: CarContext, runtime: AppRuntime) : RuntimeScreen(context, runtime) {
    override fun onGetTemplate(): Template = PaneTemplate.Builder(Pane.Builder()
        .addRow(Row.Builder().setTitle("캘린더 상태").addText("권한 거부 또는 장소가 있는 일정이 없는 상태를 확인해요.").build())
        .addAction(Action.Builder().setTitle("권한 거부").setOnClickListener {
            runtime.setScenario(DemoScenario.CALENDAR_DENIED); screenManager.popToRoot()
        }.build())
        .addAction(Action.Builder().setTitle("일정 없음").setOnClickListener {
            runtime.setScenario(DemoScenario.NO_EVENTS); screenManager.popToRoot()
        }.build()).build()).setTitle("캘린더 시연").setHeaderAction(Action.BACK).build()
}
