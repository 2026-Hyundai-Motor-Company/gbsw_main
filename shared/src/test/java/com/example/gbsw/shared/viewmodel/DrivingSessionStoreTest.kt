package com.example.gbsw.shared.viewmodel

import com.example.gbsw.shared.model.DrivingWarning
import com.example.gbsw.shared.model.PlaceCategory
import org.junit.Assert.*
import org.junit.Test

class DrivingSessionStoreTest {
    private var now = 0L
    private fun demo() = DrivingSessionStore(true, { now }).also { it.startDemo() }

    @Test fun `release store cannot enable sample data or warnings`() {
        val store = DrivingSessionStore(false)
        store.startDemo()
        store.driveFor(200)
        store.setDrowsy(true)
        assertFalse(store.uiState.value.isDemo)
        assertTrue(store.places(PlaceCategory.REST).isEmpty())
        assertNull(store.nextWarning())
    }

    @Test fun `nine stopped minutes preserve duration and ten reset it`() {
        val store = demo()
        store.stopFor(9)
        assertEquals(72, store.uiState.value.drivingMinutes)
        store.stopFor(1)
        assertEquals(0, store.uiState.value.drivingMinutes)
    }

    @Test fun `driving interrupts the consecutive stop interval`() {
        val store = demo()
        store.stopFor(9)
        store.driveFor(1)
        store.stopFor(1)
        assertEquals(73, store.uiState.value.drivingMinutes)
    }

    @Test fun `rest alert starts at 120 minutes and returns after 30 minutes`() {
        val store = demo()
        store.driveFor(47)
        assertNull(store.nextWarning())
        store.driveFor(1)
        assertEquals(DrivingWarning.REST, store.nextWarning())
        store.acknowledge(DrivingWarning.REST)
        store.driveFor(29)
        assertNull(store.nextWarning())
        store.driveFor(1)
        assertEquals(DrivingWarning.REST, store.nextWarning())
    }

    @Test fun `overspeed is strict and unavailable speed cancels it`() {
        val store = demo()
        store.setSpeed(100)
        assertNull(store.nextWarning())
        store.setSpeed(101)
        assertEquals(DrivingWarning.SPEEDING, store.nextWarning())
        store.setSpeed(null)
        assertNull(store.nextWarning())
    }

    @Test fun `warning is suppressed for five real minutes after acknowledgement`() {
        val store = demo()
        store.setDrowsy(true)
        assertEquals(DrivingWarning.DROWSY, store.nextWarning())
        store.acknowledge(DrivingWarning.DROWSY)
        now = 299_999
        assertNull(store.nextWarning())
        now = 300_000
        assertEquals(DrivingWarning.DROWSY, store.nextWarning())
    }

    @Test fun `drowsiness takes priority and stopping clears it`() {
        val store = demo()
        store.driveFor(48)
        store.setSpeed(101)
        store.setDrowsy(true)
        assertEquals(DrivingWarning.DROWSY, store.nextWarning())
        store.stopFor(10)
        assertNull(store.nextWarning())
    }

    @Test fun `leaving demo clears sample places and all driving inputs`() {
        val store = demo()
        assertEquals(listOf(12.0, 28.0, 36.0), store.places(PlaceCategory.REST).map { it.distanceKm })
        store.stopDemo()
        assertTrue(store.places(PlaceCategory.REST).isEmpty())
        assertNull(store.uiState.value.speedKph)
        assertEquals("0분", store.uiState.value.durationText)
        assertNull(store.nextWarning())
    }
}
