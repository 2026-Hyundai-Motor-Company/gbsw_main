package com.example.gbsw.shared.model

import kotlin.math.*

object GeoMath {
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 6371.0088 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        return (Math.toDegrees(atan2(sin(dLon) * cos(lat2),
            cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360) % 360
    }

    fun nearby(origin: GeoPoint, places: List<Place>, category: PlaceCategory): List<Place> =
        places.map { it.copy(distanceKm = distanceKm(origin, GeoPoint(it.latitude, it.longitude))) }
            .filter { it.distanceKm <= if (category == PlaceCategory.REST) 60 else 20 }
            .filter {
                if (category != PlaceCategory.REST || origin.bearing == null || it.distanceKm < 0.3) true
                else {
                    val direction = bearing(origin, GeoPoint(it.latitude, it.longitude))
                    val difference = abs((direction - origin.bearing + 540) % 360 - 180)
                    difference <= 90
                }
            }.distinctBy { it.id }.sortedBy { it.distanceKm }
}
