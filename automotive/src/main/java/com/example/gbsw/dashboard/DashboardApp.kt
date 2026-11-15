package com.example.gbsw.dashboard

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gbsw.BuildConfig
import com.example.gbsw.shared.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardApp(model: DashboardModel, requestPermissions: (List<String>) -> Unit) {
    val driving = model.driving.collectAsState().value
    val revision = model.updates.collectAsState().value
    val preferences = LocalContext.current.getSharedPreferences("dashboard_design", 0)
    var night by remember { mutableStateOf(preferences.getBoolean("night", false)) }
    var overlay by remember { mutableStateOf<String?>(null) }
    val warning = model.visibleWarning()
    val colors = DesignColors(night)
    BackHandler(enabled = overlay != null || warning != null || model.navigationError != null || model.tab != DashboardTab.DRIVE) {
        when {
            overlay != null -> overlay = null
            model.navigationError != null -> model.clearNavigationError()
            warning != null -> model.acknowledge()
            else -> model.select(DashboardTab.DRIVE)
        }
    }
    MaterialTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
            // Preserve the design's horizontal grid while deriving content height from the viewport.
            val scale = constraints.maxWidth / 1280f
            val designHeight = constraints.maxHeight / scale
            val layout = DashboardLayout(designHeight, LocalDensity.current.fontScale)
            CompositionLocalProvider(LocalDensity provides Density(scale, LocalDensity.current.fontScale), LocalDesign provides colors,
                LocalDashboardLayout provides layout) {
                val layer = rememberGraphicsLayer()
                if (model.tab == DashboardTab.CALENDAR && overlay == null && warning == null) {
                    CalendarWorkspace(model, model.calendar(), model.navigationError, driving.isDemo, requestPermissions) { overlay = "settings" }
                } else {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }.background(colors.background)) {
                        val title = when {
                            overlay == "settings" -> "차량 연결과 설정"
                            overlay == "demo" -> "시연 도구"
                            model.navigationError != null -> "길안내"
                            warning != null -> "안전 알림"
                            else -> model.tab.label
                        }
                        PageHeader(title, overlay != null || warning != null || model.navigationError != null, driving.isDemo,
                            onBack = { if (overlay != null) overlay = null else if (model.navigationError != null) model.clearNavigationError() else model.acknowledge() },
                            onSettings = { overlay = "settings" })
                        when {
                            overlay == "settings" -> SettingsPanel(model, night, requestPermissions,
                                onTheme = { night = !night; preferences.edit().putBoolean("night", night).apply() },
                                onDemo = { overlay = "demo" })
                            overlay == "demo" -> DemoPanel(model) { overlay = null }
                            model.navigationError != null -> StatePanel("길안내 앱을 찾을 수 없어요", "정차 후 길안내 앱이 설치되어 있는지 확인해 주세요.",
                                "states/imgIconNav.svg", if (model.tab == DashboardTab.CALENDAR) "일정으로" else "장소 목록으로", null,
                                onPrimary = model::clearNavigationError)
                            warning != null -> WarningPanel(warning, model.feedback.ghostEnabled,
                                onRest = { model.acknowledge(true) }, onConfirm = { model.acknowledge() })
                            model.tab == DashboardTab.DRIVE -> DrivingHome(driving, model, requestPermissions) { overlay = "settings" }
                            model.tab == DashboardTab.REST || model.tab == DashboardTab.SIGHTS -> PlacesContent(model, requestPermissions)
                            else -> CalendarContent(model, model.calendar(), requestPermissions)
                        }
                    }
                    if (overlay == null && warning == null) {
                        GlassTabs(model.tab, layer) { model.select(it) }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, back: Boolean, demo: Boolean, onBack: () -> Unit, onSettings: () -> Unit) {
    val layout = LocalDashboardLayout.current
    Row(Modifier.offset(48.dp, layout.headerTop.dp).width(1184.dp).height(layout.headerHeight.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back) {
            Box(Modifier.size(64.dp).clickable(onClick = onBack).semantics { contentDescription = "뒤로" }, contentAlignment = Alignment.Center) {
                FigmaIcon("150-3241/imgFrame.svg", 36, tint = LocalDesign.current.text)
            }
            Spacer(Modifier.width(16.dp))
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Copy(title, 32, true)
            if (demo) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(LocalDesign.current.surface).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Copy("시연 중", 16, color = LocalDesign.current.secondary)
            }
        }
        QuietButton("설정", Modifier.width(96.dp).semantics { contentDescription = "차량 연결과 설정" }, onSettings)
    }
}

@Composable
private fun DrivingHome(state: DrivingState, model: DashboardModel, requestPermissions: (List<String>) -> Unit, onConnections: () -> Unit) {
    val layout = LocalDashboardLayout.current
    val restState = model.places(PlaceCategory.REST)
    val eventState = model.calendar()
    val modifier = Modifier.offset(48.dp, layout.contentTop.dp).size(1184.dp, layout.contentHeight.dp)
    val openRest = { model.select(DashboardTab.REST) }
    val openNearest = {
        model.select(DashboardTab.REST)
        if (restState.status == LoadStatus.PERMISSION)
            requestPermissions(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    val connectCalendar = {
        if (state.isDemo) model.setScenario(DemoScenario.NORMAL)
        else requestPermissions(listOf(Manifest.permission.READ_CALENDAR))
        model.select(DashboardTab.CALENDAR)
    }
    if (layout.largeText) {
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            DrivingTimeCard(state, Modifier.fillMaxWidth().height((420f * layout.fontScale).dp), openRest, onConnections, stacked = true)
            NearestRestCard(restState, state.isDemo, Modifier.fillMaxWidth().height(440.dp), openNearest)
            NextEventCard(eventState, Modifier.fillMaxWidth().height(440.dp), connectCalendar) { model.select(DashboardTab.CALENDAR) }
        }
    } else Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        DrivingTimeCard(state, Modifier.fillMaxWidth().weight(.52f), openRest, onConnections)
        Row(Modifier.fillMaxWidth().weight(.48f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            NearestRestCard(restState, state.isDemo, Modifier.weight(1f).fillMaxHeight(), openNearest)
            NextEventCard(eventState, Modifier.weight(1f).fillMaxHeight(), connectCalendar) { model.select(DashboardTab.CALENDAR) }
        }
    }
}

@Composable
private fun DrivingTimeCard(state: DrivingState, modifier: Modifier, onRest: () -> Unit, onConnections: () -> Unit, stacked: Boolean = false) {
    val c = LocalDesign.current
    DesignCard(modifier, 28) {
        val summary: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Copy("연속 운전", 22, color = c.secondary)
                    val duration = buildAnnotatedString {
                        fun number(value: Int) = withStyle(SpanStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 88.sp)) { append(value.toString()) }
                        fun unit(value: String) = withStyle(SpanStyle(fontFamily = KoreanFont, fontWeight = FontWeight.Medium, fontSize = 32.sp)) { append(value) }
                        if (state.drivingMinutes >= 60) { number(state.drivingMinutes / 60); unit("시간 ") }
                        number(state.drivingMinutes % 60); unit("분")
                    }
                    Text(duration, color = c.text, lineHeight = 100.sp, letterSpacing = (-1).sp, maxLines = 2,
                        modifier = Modifier.semantics { contentDescription = "연속 운전 ${state.durationText}" })
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Copy(if (state.speedKph == null) "속도 정보 없음" else if (state.speedKph == 0) "정차 중" else "현재 속도", 22, color = c.secondary, modifier = Modifier.alignByBaseline())
                        if (state.speedKph == null) QuietButton("차량 연결", Modifier.width(144.dp), onConnections)
                        else Copy(if (state.speedKph == 0) "${state.stoppedMinutes}분" else "${state.speedKph} km/h", 28, true, modifier = Modifier.alignByBaseline())
                    }
                    if (state.speedKph == null) Copy("운전 시간 측정을 잠시 멈췄어요", 20, color = c.secondary, lines = 2)
                    else if (state.speedKph == 0 && state.stoppedMinutes in 1..9)
                        Copy("${10 - state.stoppedMinutes}분 더 쉬면 운전 시간이 초기화돼요", 20, color = c.secondary, lines = 2)
                }
            }
        }
        val restAction: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Copy("휴식 알림", 22, color = c.secondary)
                    Copy(when {
                        state.speedKph == null -> "연결하면 휴식 시점을 알려드려요"
                        state.stoppedMinutes >= 10 -> "충분히 쉬었어요"
                        state.drivingMinutes >= 120 -> "잠시 쉬어갈 시간이에요"
                        else -> "${120 - state.drivingMinutes}분 후"
                    }, 32, true, lines = 2)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.border)) {
                        Box(Modifier.fillMaxWidth((state.drivingMinutes / 120f).coerceIn(0f, 1f)).fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp)).background(c.accent))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Copy("0분", 18, color = c.secondary); Copy("2시간", 18, color = c.secondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DesignButton("휴게소 찾기", Modifier.width(if (stacked) 360.dp else 224.dp), height = 64, size = 24, onClick = onRest)
                }
            }
        }
        if (stacked) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)) {
            summary(); restAction()
        } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { summary() }
            Spacer(Modifier.width(32.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(c.border))
            Spacer(Modifier.width(32.dp))
            Box(Modifier.weight(1f)) { restAction() }
        }
    }
}

@Composable
private fun NearestRestCard(state: FeatureState<List<Place>>, demo: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDesign.current
    val place = state.data?.firstOrNull()
    DesignCard(modifier.clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}, 28) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FigmaIcon(if (c.night) "night/coffee.svg" else "150-3219/imgIconCoffee.svg", 28)
                Copy("가까운 휴게소", 22, color = c.secondary)
            }
            if (place != null) Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                Copy(place.name, 32, true, modifier = Modifier.weight(1f).alignByBaseline(), lines = 2)
                Column(Modifier.alignBy(FirstBaseline), horizontalAlignment = Alignment.End) {
                    Copy(distance(place.distanceKm), 32, true)
                    Copy(if (demo) "예시 거리" else "직선거리", 18, color = c.secondary)
                }
            } else Column(Modifier.heightIn(min = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Copy(when (state.status) {
                    LoadStatus.LOADING -> "주변 휴게소를 찾고 있어요"
                    LoadStatus.ERROR -> "휴게소를 다시 찾아볼까요?"
                    LoadStatus.EMPTY -> "주변에 휴게소가 없어요"
                    else -> "내 주변 쉴 곳을 찾아봐요"
                }, 32, true, lines = 2)
                Copy(if (state.status == LoadStatus.PERMISSION) "위치를 연결하면 가까운 순으로 보여드려요" else "목록에서 쉬어갈 곳을 선택해요", 24, color = c.secondary, lines = 2)
            }
            CardAction(if (place != null) "휴게소 선택" else if (state.status == LoadStatus.PERMISSION) "위치 연결" else "휴게소 찾기")
        }
    }
}

@Composable
private fun NextEventCard(state: FeatureState<CalendarEvent>, modifier: Modifier, onConnect: () -> Unit, onOpen: () -> Unit) {
    val c = LocalDesign.current
    val event = state.data
    val needsPermission = state.status == LoadStatus.PERMISSION
    DesignCard(modifier.clickable(role = Role.Button, onClick = if (needsPermission) onConnect else onOpen)
        .semantics(mergeDescendants = true) {}, 28) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FigmaIcon(if (c.night) "night/calendar.svg" else "150-3219/imgIconCal.svg", 28)
                Copy("다음 일정" + (event?.let { " · ${eventTime(it)}" } ?: ""), 22, color = c.secondary)
            }
            Column(Modifier.heightIn(min = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Copy(event?.title ?: when (state.status) {
                    LoadStatus.PERMISSION -> "다음 목적지를 바로 확인해요"
                    LoadStatus.LOADING -> "다음 일정을 확인하고 있어요"
                    LoadStatus.ERROR -> "일정을 다시 불러와 주세요"
                    else -> "예정된 목적지가 없어요"
                }, 32, true, lines = 2)
                Copy(event?.location ?: if (needsPermission) "캘린더에서 장소가 있는 일정을 불러와요" else "장소가 있는 오늘의 일정을 보여드려요", 24, color = c.secondary, lines = 2)
            }
            CardAction(if (event != null) "목적지 확인" else if (needsPermission) "캘린더 연결" else "일정 확인")
        }
    }
}

@Composable
private fun CardAction(label: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Copy(label, 24, color = LocalDesign.current.link)
        FigmaIcon("150-3227/imgIconNext.svg", 20, tint = LocalDesign.current.link)
    }
}

@Composable
private fun GlassTabs(tab: DashboardTab, background: GraphicsLayer, onSelect: (DashboardTab) -> Unit) {
    val c = LocalDesign.current
    val layout = LocalDashboardLayout.current
    val top = layout.navigationTop
    val density = LocalDensity.current
    val shape = RoundedCornerShape(42.dp)
    Box(Modifier.offset(176.dp, top.dp).size(928.dp, layout.navigationHeight.dp)
        .clip(shape)) {
        // Sample beyond the capsule so nearby card edges also diffuse into the glass.
        // The enlarged backdrop alone is blurred; tab labels and icons remain sharp.
        Canvas(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
            .offset((-96).dp, (-96).dp).size(1120.dp, (layout.navigationHeight + 192f).dp)
            .graphicsLayer { renderEffect = BlurEffect(64f * density.density, 64f * density.density, TileMode.Clamp) }) {
            with(drawContext.canvas) {
                save(); translate((-176 + 96).dp.toPx(), (-top + 96).dp.toPx()); drawLayer(background); restore()
            }
        }
        Box(Modifier.fillMaxSize().background(c.glass)
            .background(Brush.verticalGradient(listOf(
                Color.White.copy(if (c.night) .16f else .50f),
                Color.White.copy(.015f), Color.White.copy(if (c.night) .035f else .16f)
            ))))
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            DashboardTab.entries.forEach { item ->
                val active = item == tab
                Row(Modifier.size(228.dp, (layout.navigationHeight - 12f).dp).clip(RoundedCornerShape(36.dp))
                    .then(if (active) Modifier.background(Brush.verticalGradient(listOf(
                        c.selected, c.selected.copy(alpha = if (c.night) .16f else .12f)
                    ))) else Modifier)
                    .clickable { onSelect(item) }.semantics { role = Role.Tab; selected = active; contentDescription = item.label },
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    FigmaIcon(iconAsset(item, active, c.night), 32, tint = if (active) c.link else c.secondary)
                    Copy(item.label, 26, active, if (active) c.link else c.secondary)
                }
            }
        }
    }
}

@Composable
private fun PlacesContent(model: DashboardModel, requestPermissions: (List<String>) -> Unit) {
    val driving by model.driving.collectAsState()
    val category = if (model.tab == DashboardTab.REST) PlaceCategory.REST else PlaceCategory.SIGHT
    val state = model.places(category)
    when (state.status) {
        LoadStatus.PERMISSION -> StatePanel("현재 위치를 확인할 수 없어요", state.message,
            "states/imgIconPinOff.svg", "위치 허용", "운전 홈으로",
            onPrimary = { requestPermissions(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
            onSecondary = { model.select(DashboardTab.DRIVE) })
        LoadStatus.EMPTY -> StatePanel("주변에 ${category.title}가 없어요", "현재 위치 주변에서 찾지 못했어요.\n위치가 바뀌면 다시 검색해 보세요.",
            "150-3283/imgIconEmpty.svg", "다시 찾기", "운전 홈으로",
            onPrimary = { model.refreshPlaces(category) }, onSecondary = { model.select(DashboardTab.DRIVE) })
        LoadStatus.ERROR -> StatePanel(if (model.scenario == DemoScenario.LOCATION_ERROR) "현재 위치를 확인할 수 없어요" else "${category.title}를 불러오지 못했어요",
            if (model.scenario == DemoScenario.LOCATION_ERROR) "위치 정보를 받지 못해 ${category.title}를 찾을 수 없어요." else state.message,
            if (model.scenario == DemoScenario.LOCATION_ERROR) "states/imgIconPinOff.svg" else "150-3283/imgIconEmpty.svg", "다시 시도", "운전 홈으로",
            onPrimary = { model.refreshPlaces(category) }, onSecondary = { model.select(DashboardTab.DRIVE) })
        else -> Row(Modifier.offset(48.dp, LocalDashboardLayout.current.contentTop.dp).size(1184.dp, LocalDashboardLayout.current.contentHeight.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DesignCard(Modifier.width(580.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Copy(if (category == PlaceCategory.REST) "가까운 휴게소" else "주변 볼거리", 28, true, modifier = Modifier.weight(1f).alignByBaseline())
                            if (!state.data.isNullOrEmpty()) Copy("${state.data.orEmpty().size}곳", 24, color = LocalDesign.current.secondary, modifier = Modifier.alignByBaseline())
                        }
                        Copy(if (driving.isDemo) "가까운 순 · 예시 거리" else "가까운 순 · 직선거리", 24, color = LocalDesign.current.secondary)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalDesign.current.border))
                    if (state.status == LoadStatus.LOADING || state.status == LoadStatus.IDLE) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                androidx.compose.material3.CircularProgressIndicator(color = LocalDesign.current.accent)
                                Copy("주변 장소를 찾고 있어요", 24, color = LocalDesign.current.secondary)
                            }
                        }
                    } else Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        state.data.orEmpty().take(6).forEachIndexed { index, place ->
                            PlaceRow(index, place) { model.navigate(place) }
                        }
                    }
                }
            }
            PlaceMap(Modifier.width(580.dp).fillMaxHeight(), driving.isDemo, state.data.orEmpty(), model.position) { model.navigate(it) }
        }
    }
}

@Composable
private fun PlaceRow(index: Int, place: Place, onClick: () -> Unit) {
    val c = LocalDesign.current
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "${place.name}, ${distance(place.distanceKm)}, 길안내 시작" }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 152.dp).padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).alignBy(FirstBaseline).clip(RoundedCornerShape(20.dp)).background(c.accentSurface), contentAlignment = Alignment.Center) {
                Copy((index + 1).toString(), 24, true, c.link)
            }
            Column(Modifier.weight(1f).alignBy(FirstBaseline), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Copy(place.name, 28, true, lines = 2)
                Copy(distance(place.distanceKm) + place.description.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    24, color = c.secondary)
            }
            Row(Modifier.alignBy(FirstBaseline), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Copy("길안내", 22, color = c.link)
                FigmaIcon("150-3233/imgIconNav.svg", 24, tint = c.link)
            }
        }
        Box(Modifier.padding(start = 84.dp, end = 28.dp).fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun CalendarWorkspace(model: DashboardModel, state: FeatureState<CalendarEvent>, navigationError: String?, demo: Boolean,
                              requestPermissions: (List<String>) -> Unit, onSettings: () -> Unit) {
    val layout = LocalDashboardLayout.current
    val layer = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }.background(LocalDesign.current.background)) {
            PageHeader(if (navigationError != null) "길안내" else "일정", navigationError != null, demo,
                onBack = model::clearNavigationError, onSettings = onSettings)
            // Start the date and compact card directly below the fixed header.
            // Only this bounded content region scrolls; navigation stays at the bottom.
            Column(Modifier.offset(48.dp, layout.contentTop.dp).size(1184.dp, layout.contentHeight.dp)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Top) {
                if (navigationError != null) {
                    StatePanel("길안내 앱을 찾을 수 없어요", "정차 후 길안내 앱이 설치되어 있는지 확인해 주세요.",
                        "states/imgIconNav.svg", "일정으로", null, onPrimary = model::clearNavigationError,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    CalendarContent(model, state, requestPermissions, Modifier.fillMaxWidth())
                }
            }
        }
        GlassTabs(DashboardTab.CALENDAR, layer, onSelect = model::select)
    }
}

@Composable
private fun CalendarContent(model: DashboardModel, state: FeatureState<CalendarEvent>, requestPermissions: (List<String>) -> Unit, modifier: Modifier? = null) {
    val event = state.data
    when {
        state.status == LoadStatus.PERMISSION -> StatePanel("다음 목적지를 바로 확인해요", "캘린더를 연결하면 장소가 있는 다음 일정을 보여드려요.",
            "states/imgIconLock.svg", "캘린더 연결", "나중에",
            onPrimary = {
                if (model.driving.value.isDemo) model.setScenario(DemoScenario.NORMAL)
                else requestPermissions(listOf(Manifest.permission.READ_CALENDAR))
            }, onSecondary = { model.select(DashboardTab.DRIVE) }, modifier = modifier)
        state.status == LoadStatus.LOADING && event == null -> StatePanel("다음 일정을 확인하고 있어요", "장소가 있는 오늘의 다음 일정을 찾고 있어요.",
            "night/calendar.svg", "운전 홈으로", null, onPrimary = { model.select(DashboardTab.DRIVE) }, modifier = modifier)
        state.status == LoadStatus.ERROR -> StatePanel("일정을 불러오지 못했어요", "기기 캘린더 연결을 확인하고 다시 시도해주세요.",
            "150-3283/imgIconEmpty.svg", "다시 시도", "운전 홈으로", onPrimary = model::refreshCalendar,
            onSecondary = { model.select(DashboardTab.DRIVE) }, modifier = modifier)
        event == null -> StatePanel("예정된 목적지가 없어요", "캘린더 일정에 장소를 추가하면 여기에 보여드려요.",
            "states/imgIconCal.svg", "일정 새로고침", "홈으로",
            onPrimary = { if (model.driving.value.isDemo) model.setScenario(DemoScenario.NORMAL) else model.refreshCalendar() },
            onSecondary = { model.select(DashboardTab.DRIVE) }, modifier = modifier)
        else -> CalendarEventContent(event, onRefresh = model::refreshCalendar, onNavigate = { model.navigate(event) }, modifier = modifier)
    }
}

@Composable
private fun CalendarEventContent(event: CalendarEvent, onRefresh: () -> Unit, onNavigate: () -> Unit, modifier: Modifier? = null) {
    val c = LocalDesign.current
    val layout = LocalDashboardLayout.current
    val date = Instant.ofEpochMilli(event.beginMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN))
    Column(modifier ?: Modifier.offset(48.dp, layout.contentTop.dp).size(1184.dp, layout.contentHeight.dp)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Copy("오늘 · $date", 24, color = c.secondary, modifier = Modifier.weight(1f), lines = 2)
            QuietButton("새로고침", Modifier.width(if (layout.largeText) 260.dp else 136.dp), onRefresh)
        }
        DesignCard(Modifier.fillMaxWidth(), 28) {
            if (!layout.largeText) CalendarEventRow(event, onNavigate)
            else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(if (layout.largeText) 288.dp else 144.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Copy("시작", 22, color = c.secondary)
                    Copy(eventTime(event), 48, true)
                }
                Column(Modifier.weight(1f)) {
                    Copy(event.title, 36, true, lines = 3)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FigmaIcon("150-3230/imgIconPin.svg", 24, tint = c.secondary)
                        Copy(event.location, 28, color = c.secondary, modifier = Modifier.weight(1f), lines = 3)
                    }
                    if (layout.largeText) {
                        Spacer(Modifier.height(20.dp))
                        DesignButton("길안내 시작", Modifier.width(400.dp), onClick = onNavigate)
                    }
                }
                if (!layout.largeText) DesignButton("길안내 시작", Modifier.width(216.dp).align(Alignment.CenterVertically), onClick = onNavigate)
            }
        }
    }
}

@Composable
private fun CalendarEventRow(event: CalendarEvent, onNavigate: () -> Unit) {
    val c = LocalDesign.current
    // Measure two shared text rows so differing font sizes do not move the labels.
    // Anchor time and action to the inner edges. Distribute spare width across
    // the three gaps instead of stretching one field or centering the group.
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        Copy("시작", 22, color = c.secondary)
        Copy("다음 일정", 22, color = c.secondary)
        Copy("목적지", 22, color = c.secondary)
        Copy(eventTime(event), 48, true)
        Copy(event.title, 36, true, lines = 3)
        Copy(event.location, 28, lines = 3)
        DesignButton("길안내 시작", Modifier.width(216.dp), onClick = onNavigate)
    }) { items, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val columnGap = 24.dp.roundToPx()
        val actionGap = 20.dp.roundToPx()
        val rowGap = 8.dp.roundToPx()
        val timeWidth = 144.dp.roundToPx()
        val action = items[6].measure(loose.copy(maxWidth = 216.dp.roundToPx()))
        val destination = items[5].measure(loose.copy(maxWidth = 424.dp.roundToPx()))
        val availableTitleWidth = constraints.maxWidth - timeWidth - destination.width - action.width - columnGap * 2 - actionGap
        val titleLimit = minOf(240.dp.roundToPx(), availableTitleWidth)
        val title = items[4].measure(loose.copy(maxWidth = titleLimit))
        val titleWidth = maxOf(title.width, minOf(112.dp.roundToPx(), titleLimit))
        val values = listOf(items[3].measure(loose.copy(maxWidth = timeWidth)),
            title, destination)
        val widths = listOf(timeWidth, titleWidth, destination.width)
        val labels = (0..2).map { items[it].measure(loose.copy(maxWidth = widths[it])) }
        val labelBaseline = labels.maxOf { it[FirstBaseline] }
        val labelHeight = labels.maxOf { labelBaseline - it[FirstBaseline] + it.height }
        val valueBaseline = values.maxOf { it[FirstBaseline] }
        val valueHeight = values.maxOf { valueBaseline - it[FirstBaseline] + it.height }
        val bodyHeight = maxOf(valueHeight, action.height)
        val valueTop = labelHeight + rowGap
        val groupWidth = timeWidth + titleWidth + destination.width + action.width + columnGap * 2 + actionGap
        val gapShare = (constraints.maxWidth - groupWidth) / 3
        val gap = columnGap + gapShare
        val xs = listOf(0, timeWidth + gap, timeWidth + titleWidth + gap * 2)
        layout(constraints.maxWidth, valueTop + bodyHeight) {
            labels.forEachIndexed { index, p -> p.placeRelative(xs[index], labelBaseline - p[FirstBaseline]) }
            values.forEachIndexed { index, p -> p.placeRelative(xs[index], valueTop + valueBaseline - p[FirstBaseline]) }
            action.placeRelative(constraints.maxWidth - action.width, valueTop + (bodyHeight - action.height) / 2)
        }
    }
}

@Composable
private fun WarningPanel(warning: DrivingWarning, ghost: Boolean, onRest: () -> Unit, onConfirm: () -> Unit) {
    val isRest = warning == DrivingWarning.REST
    DesignCard(Modifier.offset(48.dp, LocalDashboardLayout.current.contentTop.dp).size(1184.dp, LocalDashboardLayout.current.contentHeight.dp), 32) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (isRest || !ghost) FigmaIcon("150-3259/imgIconCoffee.svg", 80, tint = LocalDesign.current.link)
                else Box(Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF30343D)), contentAlignment = Alignment.Center) {
                    FigmaIcon("150-3241/imgH3GhostArtwork.svg", 88)
                }
                Spacer(Modifier.height(20.dp))
                Copy(when (warning) { DrivingWarning.DROWSY -> "잠시 쉬어 가세요"; DrivingWarning.SPEEDING -> "속도를 줄여 주세요"; DrivingWarning.REST -> "쉬어 갈 시간이에요" }, 40, true)
                Copy(when (warning) { DrivingWarning.DROWSY -> "졸음이 감지됐어요"; DrivingWarning.SPEEDING -> "과속이 감지됐어요"; DrivingWarning.REST -> "2시간 연속 운전했어요" }, 24, color = LocalDesign.current.secondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                DesignButton("휴게소 찾기", Modifier.width(300.dp), onClick = onRest)
                DesignButton("알림 닫기", Modifier.width(208.dp), false, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun StatePanel(title: String, message: String, icon: String, primary: String, secondary: String?, primaryStyle: Boolean = true, primaryWidth: Int? = null, onPrimary: () -> Unit, onSecondary: () -> Unit = {}, modifier: Modifier? = null) {
    val layout = LocalDashboardLayout.current
    val c = LocalDesign.current
    Column(modifier ?: Modifier.offset(48.dp, layout.contentTop.dp).size(1184.dp, layout.contentHeight.dp)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        DesignCard(Modifier.fillMaxWidth(), 40) {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(c.surface), contentAlignment = Alignment.Center) {
                        FigmaIcon(icon, 32, tint = c.secondary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Copy(title, 32, true, lines = 2)
                        Copy(message, 24, color = c.secondary, lines = 4)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = if (layout.largeText) 0.dp else 88.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DesignButton(primary, Modifier.width((primaryWidth ?: if (layout.largeText) 400 else 256).dp), primaryStyle, onClick = onPrimary)
                    if (secondary != null) QuietButton(secondary, Modifier.width(if (layout.largeText) 320.dp else 176.dp), onSecondary)
                }
            }
        }
    }
}

private fun eventTime(event: CalendarEvent) = Instant.ofEpochMilli(event.beginMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun distance(km: Double) = if (km % 1.0 == 0.0) "${km.toInt()} km" else "%.1f km".format(km)
