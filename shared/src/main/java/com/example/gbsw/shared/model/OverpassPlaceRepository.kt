package com.example.gbsw.shared.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/** 공개 OSM 조회. 좌표와 반경만 전달하며 일정 제목·주소나 식별자를 보내지 않는다. */
class OverpassPlaceRepository(
    private val endpoint: String = DEFAULT_ENDPOINT,
) {
    private data class Cached(val origin: GeoPoint, val createdAt: Long, val places: List<Place>)
    private val cache = mutableMapOf<PlaceCategory, Cached>()

    suspend fun find(origin: GeoPoint, category: PlaceCategory): List<Place> = withContext(Dispatchers.IO) {
        val cached = synchronized(cache) { cache[category] }
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.createdAt < 300_000 &&
            GeoMath.distanceKm(cached.origin, origin) < 1.0) {
            return@withContext GeoMath.nearby(origin, cached.places, category)
        }
        val radius = if (category == PlaceCategory.REST) 60_000 else 20_000
        val filter = if (category == PlaceCategory.REST)
            """["highway"~"^(services|rest_area)$"]"""
        else """["tourism"~"^(attraction|viewpoint|museum)$"]["name"]"""
        val query = "[out:json][timeout:12];nwr(around:$radius,${origin.latitude},${origin.longitude})$filter;out center tags;"
        val connection = URL(endpoint + "?data=" + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 16_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "GBSW/1.0 (Android Automotive; educational prototype)")
            if (connection.responseCode != 200) throw IOException("장소 서버 응답 ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readNBytes(2_000_001) }
            if (bytes.size > 2_000_000) throw IOException("장소 응답이 너무 큽니다")
            coroutineContext.ensureActive()
            val body = JSONObject(bytes.toString(Charsets.UTF_8))
            if (body.has("remark")) throw IOException("장소 조회가 지연되고 있어요")
            val parsed = parse(body)
            synchronized(cache) { cache[category] = Cached(origin, now, parsed) }
            GeoMath.nearby(origin, parsed, category)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: JSONObject): List<Place> {
        val elements = body.getJSONArray("elements")
        return buildList {
            for (i in 0 until minOf(elements.length(), 2000)) {
                val item = elements.optJSONObject(i) ?: continue
                val coordinate = item.optJSONObject("center") ?: item
                val lat = coordinate.optDouble("lat", Double.NaN)
                val lon = coordinate.optDouble("lon", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                val tags = item.optJSONObject("tags") ?: continue
                val name = tags.optString("name:ko").ifBlank { tags.optString("name") }
                    .ifBlank { tags.optString("brand") }.ifBlank { "휴게 공간" }.take(100)
                val description = listOfNotNull(
                    tags.optString("addr:city").takeIf { it.isNotBlank() },
                    tags.optString("addr:street").takeIf { it.isNotBlank() },
                    if (tags.optString("highway") == "services") "고속도로 휴게소" else null,
                    if (tags.optString("highway") == "rest_area") "쉼터" else null,
                    when (tags.optString("tourism")) {
                        "attraction" -> "관광 명소"; "viewpoint" -> "전망 명소"; "museum" -> "박물관"; else -> null
                    },
                ).joinToString(" · ").take(180).ifBlank { "주변 장소" }
                add(Place("${item.optString("type")}/${item.optLong("id")}", name, 0.0, description, lat, lon))
            }
        }
    }

    companion object { const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter" }
}
