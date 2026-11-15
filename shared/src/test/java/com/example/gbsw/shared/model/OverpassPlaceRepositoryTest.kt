package com.example.gbsw.shared.model

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

class OverpassPlaceRepositoryTest {
    private val body = """{"elements":[
      {"type":"node","id":1,"lat":37.1,"lon":127.0,"tags":{"name:ko":"북쪽 휴게소","highway":"services"}},
      {"type":"way","id":2,"center":{"lat":36.9,"lon":127.0},"tags":{"name":"South","highway":"rest_area"}},
      {"type":"way","id":3,"tags":{"name":"No coordinates"}},
      {"type":"node","id":4,"lat":999,"lon":127,"tags":{"name":"Invalid"}}
    ]}"""

    @Test fun parsesNodesAndWayCentersAndRejectsInvalidCoordinates() {
        val result = OverpassPlaceRepository().parse(JSONObject(body))
        assertEquals(listOf("node/1", "way/2"), result.map { it.id })
        assertEquals("북쪽 휴게소", result.first().name)
    }

    @Test fun requestUsesOnlyCoordinatesAndCachesBeforeFilteringByBearing() = runBlocking {
        val count = AtomicInteger()
        var query = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/interpreter") { exchange ->
            count.incrementAndGet()
            query = URLDecoder.decode(exchange.requestURI.rawQuery, "UTF-8")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val source = OverpassPlaceRepository("http://127.0.0.1:${server.address.port}/api/interpreter")
            val north = source.find(GeoPoint(37.0, 127.0, 0.0), PlaceCategory.REST)
            val south = source.find(GeoPoint(37.0, 127.0, 180.0), PlaceCategory.REST)
            assertEquals("node/1", north.single().id)
            assertEquals("way/2", south.single().id)
            assertEquals(1, count.get())
            assertTrue(query.contains("around:60000,37.0,127.0"))
            assertFalse(query.contains("calendar"))
        } finally { server.stop(0) }
    }

    @Test fun serverFailureIsAnErrorInsteadOfAnEmptyOrInventedList() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> exchange.sendResponseHeaders(503, -1); exchange.close() }
        server.start()
        try {
            val source = OverpassPlaceRepository("http://127.0.0.1:${server.address.port}/")
            try { source.find(GeoPoint(37.0,127.0), PlaceCategory.REST); fail("Expected network error") }
            catch (_: java.io.IOException) { }
        } finally { server.stop(0) }
    }

    @Test fun malformedAndTimeoutResponsesDoNotBecomeValidResults() {
        try { OverpassPlaceRepository().parse(JSONObject("""{"remark":"timeout"}""")); fail("Expected parse error") }
        catch (_: org.json.JSONException) { }
    }
}
