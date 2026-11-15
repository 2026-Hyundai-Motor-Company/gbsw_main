package com.example.gbsw.shared.model

import org.junit.Assert.*
import org.junit.Test

class CalendarSelectionTest {
    @Test fun futureNamedLocationIsEligible() { assertTrue(CalendarSelection.isUpcoming(200, "고양", false, 100, 300)) }
    @Test fun eventStartingNowIsEligible() { assertTrue(CalendarSelection.isUpcoming(100, "고양", false, 100, 300)) }
    @Test fun pastEventIsNotNext() { assertFalse(CalendarSelection.isUpcoming(99, "고양", false, 100, 300)) }
    @Test fun nextDayBoundaryIsExcluded() { assertFalse(CalendarSelection.isUpcoming(300, "고양", false, 100, 300)) }
    @Test fun cancelledEventIsExcluded() { assertFalse(CalendarSelection.isUpcoming(200, "고양", true, 100, 300)) }
    @Test fun whitespaceLocationIsExcluded() { assertFalse(CalendarSelection.isUpcoming(200, "  ", false, 100, 300)) }
}
