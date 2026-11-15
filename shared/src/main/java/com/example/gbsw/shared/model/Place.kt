package com.example.gbsw.shared.model

data class Place(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val description: String,
    val latitude: Double,
    val longitude: Double,
)

enum class PlaceCategory(val title: String) {
    REST("휴게소"), SIGHT("볼거리"),
}

/** 화면 시연 전용 자료. 실제 위치·진행 방향·도로 거리를 나타내지 않는다. */
class DemoPlaceRepository {
    fun places(category: PlaceCategory): List<Place> = when (category) {
        PlaceCategory.REST -> listOf(
            Place("yeoju", "여주휴게소", 12.0, "식사 · 카페 · 주유", 37.2398, 127.5702),
            Place("munmak", "문막휴게소", 28.0, "식사 · 편의점 · 주유", 37.3008, 127.8138),
            Place("deokpyeong", "덕평휴게소", 36.0, "식사 · 카페 · 산책", 37.2422, 127.3901),
        )
        PlaceCategory.SIGHT -> listOf(
            Place("silleuksa", "신륵사", 8.0, "강변을 따라 걷는 산책", 37.2975, 127.6631),
            Place("sejong", "세종대왕릉", 16.0, "숲길 · 역사", 37.3072, 127.6042),
        )
    }.sortedBy { it.distanceKm }
}
