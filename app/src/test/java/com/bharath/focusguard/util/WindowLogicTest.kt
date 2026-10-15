package com.bharath.focusguard.util

import com.bharath.focusguard.data.local.entities.BlockWindow
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class WindowLogicTest {
    private fun mon9to17() = BlockWindow(name = "Work", daysMask = 1, startMinute = 540, endMinute = 1020)

    @Test fun daytimeInside() {
        // Monday 10:00
        assertTrue(WindowLogic.isWindowActive(LocalDateTime.of(2026, 10, 12, 10, 0), mon9to17()))
    }

    @Test fun daytimeOutside() {
        assertFalse(WindowLogic.isWindowActive(LocalDateTime.of(2026, 10, 12, 18, 0), mon9to17()))
    }

    @Test fun overnightSpansMidnight() {
        val sleep = BlockWindow(name = "Sleep", daysMask = 1, startMinute = 1350, endMinute = 360)
        // Monday 23:00 -> active (mask Monday)
        assertTrue(WindowLogic.isWindowActive(LocalDateTime.of(2026, 10, 12, 23, 0), sleep))
        // Tuesday 02:00 -> active against Monday's mask
        assertTrue(WindowLogic.isWindowActive(LocalDateTime.of(2026, 10, 13, 2, 0), sleep))
        // Tuesday 10:00 -> not active
        assertFalse(WindowLogic.isWindowActive(LocalDateTime.of(2026, 10, 13, 10, 0), sleep))
    }

    @Test fun domainMatching() {
        assertTrue(WindowLogic.domainMatches("reddit.com", "www.reddit.com"))
        assertTrue(WindowLogic.domainMatches("reddit.com", "old.reddit.com"))
        assertFalse(WindowLogic.domainMatches("reddit.com", "notreddit.com"))
    }
}
