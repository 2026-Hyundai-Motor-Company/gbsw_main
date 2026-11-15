package com.example.gbsw.shared.viewmodel

import com.example.gbsw.shared.model.DrivingWarning
import org.junit.Assert.*
import org.junit.Test

class LiveDrivingTest {
    private var now = 0L
    private fun live(initial: Long = 0) = DrivingSessionStore(false, { now }, initialDrivingMillis = initial)
    private fun advance(store: DrivingSessionStore, seconds: Int, speed: Int) {
        repeat(seconds) {
            store.onVehicleSpeed(speed)
            now += 1000
            store.tick()
        }
    }

    @Test fun liveDrivingCountsThirtyMinutesWithoutDemo() {
        val store = live()
        advance(store, 1800, 80)
        assertEquals(30, store.uiState.value.drivingMinutes)
        assertFalse(store.uiState.value.isDemo)
    }

    @Test fun stationaryNineMinutesKeepTripAndTenResetIt() {
        val store = live(30 * 60_000L)
        advance(store, 540, 0)
        assertEquals(30, store.uiState.value.drivingMinutes)
        advance(store, 60, 0)
        assertEquals(0, store.uiState.value.drivingMinutes)
        assertEquals(10, store.uiState.value.stoppedMinutes)
    }

    @Test fun unknownSignalNeverCountsAsRest() {
        val store = live(72 * 60_000L)
        store.onVehicleSpeed(null)
        now += 600_000
        store.tick()
        assertEquals(72, store.uiState.value.drivingMinutes)
        assertEquals(0, store.uiState.value.stoppedMinutes)
    }

    @Test fun staleSpeedStopsAccumulatingAfterFiveSeconds() {
        val store = live()
        store.onVehicleSpeed(85)
        now = 90_000
        store.tick()
        assertEquals(5000L, store.uiState.value.drivingMillis)
        assertNull(store.uiState.value.speedKph)
    }

    @Test fun signalGapBreaksProofOfConsecutiveRest() {
        val store = live(72 * 60_000L)
        advance(store, 540, 0)
        store.onVehicleSpeed(null)
        now += 60_000
        store.tick()
        advance(store, 60, 0)
        assertEquals(72, store.uiState.value.drivingMinutes)
        assertEquals(1, store.uiState.value.stoppedMinutes)
    }

    @Test fun liveOverspeedUsesSameBoundaryAndCooldown() {
        val store = live()
        store.onVehicleSpeed(100)
        assertNull(store.nextWarning())
        store.onVehicleSpeed(101)
        assertEquals(DrivingWarning.SPEEDING, store.nextWarning())
        store.acknowledge(DrivingWarning.SPEEDING)
        assertNull(store.nextWarning())
        advance(store, 300, 101)
        assertEquals(DrivingWarning.SPEEDING, store.nextWarning())
    }

    @Test fun restartRestoresDurationAndRemainingWarningCooldown() {
        val store = DrivingSessionStore(false, { now }, initialDrivingMillis = 120 * 60_000L,
            initialCooldowns = mapOf(DrivingWarning.REST to 60_000))
        assertEquals(120, store.uiState.value.drivingMinutes)
        assertNull(store.nextWarning())
        now = 60_000
        store.tick()
        assertEquals(DrivingWarning.REST, store.nextWarning())
    }

    @Test fun demoCannotOverwriteAnExistingLiveTripOrAcknowledgeItsWarnings() {
        val store = DrivingSessionStore(true, { now }, initialDrivingMillis = 90 * 60_000L)
        store.startDemo()
        store.driveFor(60)
        store.acknowledge(DrivingWarning.REST)
        assertEquals(90 * 60_000L, store.persistedDrivingMillis())
        store.stopDemo()
        assertEquals(90, store.uiState.value.drivingMinutes)
        assertNull(store.uiState.value.speedKph)
        assertEquals(0, store.uiState.value.stoppedMinutes)
    }

    @Test fun newTripAfterResetStartsAtZeroAndRecordsFreshMotion() {
        val store = live(120 * 60_000L)
        advance(store, 600, 0)
        advance(store, 30, 80)
        assertEquals(30_000L, store.uiState.value.drivingMillis)
        assertEquals(0, store.uiState.value.stoppedMinutes)
    }
}
