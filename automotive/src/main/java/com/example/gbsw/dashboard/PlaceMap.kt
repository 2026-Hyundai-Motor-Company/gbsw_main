package com.example.gbsw.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gbsw.shared.model.GeoPoint
import com.example.gbsw.shared.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

private val inversion = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f,
)))

@Composable
fun PlaceMap(modifier: Modifier, demo: Boolean, places: List<Place>, origin: GeoPoint?, navigate: (Place) -> Unit) {
    DesignCard(modifier) {
        if (demo) FigmaExampleMap(places, navigate) else LiveMap(places, origin, navigate)
        Copy("© OpenStreetMap contributors", 12, color = if (LocalDesign.current.night) Color.White else Color(0xFF44464E),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp))
    }
}

@Composable
private fun FigmaExampleMap(places: List<Place>, navigate: (Place) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxOf(maxWidth.value / 580f, maxHeight.value / 536f)
        Box(Modifier.align(Alignment.TopStart).requiredSize(580.dp, 536.dp).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale; scaleY = scale
        }) { FigmaMapContent(places, navigate) }
    }
}

@Composable
private fun FigmaMapContent(places: List<Place>, navigate: (Place) -> Unit) {
    val assets = LocalContext.current.assets
    val night = LocalDesign.current.night
    Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).offset((-681).dp, (-145).dp).size(1280.dp, 800.dp)) {
        (0..5).forEach { index ->
            val image = remember(index) { assets.open("figma/150-3227/imgMapTile$index.png").use { BitmapFactory.decodeStream(it).asImageBitmap() } }
            Image(image, null, Modifier.offset(((index % 3) * 426.6667f).dp, (if (index < 3) -26.6667f else 400f).dp)
                .size(426.6667f.dp), contentScale = ContentScale.FillBounds, colorFilter = if (night) inversion else null)
        }
        if (night) Box(Modifier.fillMaxSize().background(Color.White.copy(.06f)))
        val positions = listOf(900 to 218, 1080 to 418, 808 to 604)
        places.take(3).forEachIndexed { index, place ->
            Marker(index, Modifier.offset(positions[index].first.dp, positions[index].second.dp), 48) { navigate(place) }
        }
    }
    FigmaIcon("150-3227/imgExampleCurrentLocation.svg", 18, Modifier.offset(57.dp, 415.dp))
}

private data class MapProjection(val zoom: Int, val left: Double, val top: Double) {
    val world get() = 256.0 * 2.0.pow(zoom)
    fun point(latitude: Double, longitude: Double): Pair<Double, Double> {
        val p = normalized(latitude, longitude)
        return p.first * world - left to p.second * world - top
    }
    companion object {
        fun normalized(lat: Double, lon: Double): Pair<Double, Double> {
            val sin = sin(Math.toRadians(lat.coerceIn(-85.0, 85.0)))
            return (lon + 180) / 360 to (.5 - ln((1 + sin) / (1 - sin)) / (4 * PI))
        }
        fun fit(places: List<Place>, origin: GeoPoint?, width: Float, height: Float): MapProjection? {
            val coordinates = places.take(6).map { normalized(it.latitude, it.longitude) }.toMutableList()
            origin?.let { coordinates += normalized(it.latitude, it.longitude) }
            if (coordinates.isEmpty()) return null
            val minX = coordinates.minOf { it.first }; val maxX = coordinates.maxOf { it.first }
            val minY = coordinates.minOf { it.second }; val maxY = coordinates.maxOf { it.second }
            val zoom = if (coordinates.size == 1) 12 else floor(log2(min((width - 96) / ((maxX - minX).coerceAtLeast(.00001) * 256), (height - 96) / ((maxY - minY).coerceAtLeast(.00001) * 256)))).toInt().coerceIn(3, 14)
            val world = 256.0 * 2.0.pow(zoom)
            return MapProjection(zoom, (minX + maxX) / 2 * world - width / 2, (minY + maxY) / 2 * world - height / 2)
        }
    }
}
private data class Tile(val x: Float, val y: Float, val image: ImageBitmap)
private data class TileResult(val tiles: List<Tile> = emptyList(), val loaded: Boolean = false)

@Composable
private fun LiveMap(places: List<Place>, origin: GeoPoint?, navigate: (Place) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LiveMapContent(places, origin, maxWidth.value, maxHeight.value, navigate)
    }
}

@Composable
private fun LiveMapContent(places: List<Place>, origin: GeoPoint?, width: Float, height: Float, navigate: (Place) -> Unit) {
    val context = LocalContext.current
    val projection = remember(places, origin, width, height) { MapProjection.fit(places, origin, width, height) }
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState(TileResult(), projection, retry) {
        value = TileResult()
        value = if (projection == null) TileResult(loaded = true) else withContext(Dispatchers.IO) {
            val tiles = mutableListOf<Tile>()
            val startX = floor(projection.left / 256).toInt(); val endX = floor((projection.left + width) / 256).toInt()
            val startY = floor(projection.top / 256).toInt(); val endY = floor((projection.top + height) / 256).toInt()
            for (y in startY..endY) for (x in startX..endX) {
                val count = 1 shl projection.zoom
                if (y !in 0 until count) continue
                val wrappedX = ((x % count) + count) % count
                try {
                    val file = File(context.cacheDir, "osm_tiles/${projection.zoom}_${wrappedX}_$y.png")
                    // Request only visible tiles. Persist for at least seven days per OSM policy.
                    if (!file.exists() || System.currentTimeMillis() - file.lastModified() > 7 * 86_400_000L) {
                        val connection = URL("https://tile.openstreetmap.org/${projection.zoom}/$wrappedX/$y.png").openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
                            connection.setRequestProperty("User-Agent", "GBSW/1.0 (Android Automotive educational prototype)")
                            if (connection.responseCode == 200) {
                                val bytes = connection.inputStream.use { it.readNBytes(512_001) }
                                if (bytes.size <= 512_000 && BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                                    file.parentFile?.mkdirs(); file.writeBytes(bytes)
                                }
                            }
                        } finally { connection.disconnect() }
                    }
                    if (file.exists()) BitmapFactory.decodeFile(file.path)?.let {
                        tiles += Tile((x * 256 - projection.left).toFloat(), (y * 256 - projection.top).toFloat(), it.asImageBitmap())
                    }
                } catch (_: Exception) { /* Partial tiles never replace the list or prevent navigation. */ }
            }
            TileResult(tiles, true)
        }
    }
    val night = LocalDesign.current.night
    Box(Modifier.fillMaxSize()) {
        result.tiles.forEach { tile ->
            Image(tile.image, null, Modifier.offset(tile.x.dp, tile.y.dp).size(256.dp), contentScale = ContentScale.FillBounds, colorFilter = if (night) inversion else null)
        }
        if (night && result.tiles.isNotEmpty()) Box(Modifier.fillMaxSize().background(Color.White.copy(.06f)))
        if (projection != null) {
            places.take(6).forEachIndexed { index, place ->
                val (x, y) = projection.point(place.latitude, place.longitude)
                Marker(index, Modifier.offset((x - 24).dp, (y - 24).dp), 48) { navigate(place) }
            }
            origin?.let {
                val (x, y) = projection.point(it.latitude, it.longitude)
                FigmaIcon("150-3227/imgExampleCurrentLocation.svg", 18, Modifier.offset((x - 9).dp, (y - 9).dp))
            }
        }
        if (result.tiles.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Copy(if (!result.loaded) "지도를 불러오고 있어요" else if (projection == null) "위치를 확인하면 지도를 표시해요" else "지도를 불러오지 못했어요", 22, color = LocalDesign.current.secondary)
                if (result.loaded && projection != null) QuietButton("다시 시도") { retry++ }
            }
        }
    }
}

@Composable
private fun Marker(index: Int, modifier: Modifier, size: Int, onClick: () -> Unit) {
    Box(modifier.size(size.dp).clip(CircleShape).background(LocalDesign.current.accent).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Copy((index + 1).toString(), 26, true, Color.White)
    }
}
