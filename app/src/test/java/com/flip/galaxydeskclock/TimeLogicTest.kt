package com.flip.galaxydeskclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeLogicTest {
    private val zone = ZoneId.of("Asia/Bangkok")

    @Test
    fun normalBeforeRestWindow() {
        val now = ZonedDateTime.of(2026, 7, 16, 10, 57, 59, 0, zone)
        val result = TimeLogic.resolve(now.toInstant().toEpochMilli(), zone, true, 58, 5)
        assertEquals(DisplayPhase.NORMAL, result.phase)
    }

    @Test
    fun restStartsAtMinute58() {
        val now = ZonedDateTime.of(2026, 7, 16, 10, 58, 0, 0, zone)
        val result = TimeLogic.resolve(now.toInstant().toEpochMilli(), zone, true, 58, 5)
        assertEquals(DisplayPhase.REST, result.phase)
    }

    @Test
    fun fadeStartsFiveSecondsBeforeHour() {
        val now = ZonedDateTime.of(2026, 7, 16, 10, 59, 55, 0, zone)
        val result = TimeLogic.resolve(now.toInstant().toEpochMilli(), zone, true, 58, 5)
        assertEquals(DisplayPhase.FADE_IN, result.phase)
        assertEquals(0f, result.nextLayoutAlpha, 0.001f)
    }

    @Test
    fun fadeApproachesOne() {
        val now = ZonedDateTime.of(2026, 7, 16, 10, 59, 59, 500_000_000, zone)
        val result = TimeLogic.resolve(now.toInstant().toEpochMilli(), zone, true, 58, 5)
        assertEquals(DisplayPhase.FADE_IN, result.phase)
        assertTrue(result.nextLayoutAlpha > 0.85f)
    }

    @Test
    fun layoutsDoNotRepeatOnConsecutiveHours() {
        val enabled = (0 until 6).toList()
        var seed = 1_700_000_000L
        repeat(200) {
            val current = TimeLogic.layoutIndex(seed, enabled)
            val next = TimeLogic.layoutIndex(seed + 3_600L, enabled)
            assertTrue(current != next)
            seed += 3_600L
        }
    }
}
