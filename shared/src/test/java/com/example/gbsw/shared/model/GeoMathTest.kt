package com.example.gbsw.shared.model

import org.junit.Assert.*
import org.junit.Test

class GeoMathTest {
    private val origin = GeoPoint(37.0, 127.0, 0.0)
    private fun place(id: String, lat: Double, lon: Double) = Place(id, id, 999.0, "", lat, lon)
    @Test fun samePointHasZeroDistance() {
        assertEquals(0.0, GeoMath.distanceKm(origin, origin), 0.00001)
    }
    @Test fun knownLatitudeDifferenceIsApproximatelyElevenKm() {
        assertEquals(11.12, GeoMath.distanceKm(origin, GeoPoint(37.1, 127.0)), 0.02)
    }
    @Test fun restStopsBehindTravelDirectionAreExcludedAndResultsSorted() {
        val result = GeoMath.nearby(origin, listOf(
            place("far", 37.2, 127.0), place("behind", 36.9, 127.0),
            place("near", 37.1, 127.0), place("out", 38.0, 127.0)), PlaceCategory.REST)
        assertEquals(listOf("near", "far"), result.map { it.id })
    }
    @Test fun bearingAcrossNorthWrapsCorrectly() {
        val result = GeoMath.nearby(origin.copy(bearing = 359.0),
            listOf(place("north", 37.1, 127.005)), PlaceCategory.REST)
        assertEquals(1, result.size)
    }
    @Test fun noBearingIncludesBothDirectionsAndSightseeingUsesTwentyKmRadius() {
        val result = GeoMath.nearby(origin.copy(bearing = null), listOf(
            place("north", 37.1, 127.0), place("south", 36.95, 127.0), place("far", 37.4, 127.0)), PlaceCategory.SIGHT)
        assertEquals(listOf("south", "north"), result.map { it.id })
    }
    @Test fun duplicateSourceIdsAreRemoved() {
        assertEquals(1, GeoMath.nearby(origin, listOf(place("same",37.1,127.0), place("same",37.1,127.0)), PlaceCategory.REST).size)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesRejected() { GeoPoint(Double.NaN, 127.0) }
}
