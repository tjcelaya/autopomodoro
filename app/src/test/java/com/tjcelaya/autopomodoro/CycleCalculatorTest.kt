package com.tjcelaya.autopomodoro

import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.scheduler.CycleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CycleCalculatorTest {

    private fun schedule(
        daysOn: Int = 4,
        daysOff: Int = 3,
        cycleStart: LocalDate = LocalDate.of(2026, 1, 1), // Thursday
        windowStart: LocalTime = LocalTime.of(9, 0),
        windowEnd: LocalTime = LocalTime.of(17, 0),
        intervalMinutes: Int = 60,
        enabled: Boolean = true,
    ) = PomodoroSchedule(
        id = 1,
        name = "Test",
        cycleStartDate = cycleStart,
        daysOn = daysOn,
        daysOff = daysOff,
        windowStart = windowStart,
        windowEnd = windowEnd,
        intervalMinutes = intervalMinutes,
        isEnabled = enabled,
    )

    // ── isActiveDay ──

    @Test
    fun `day 0 of cycle is active`() {
        val s = schedule()
        assertTrue(CycleCalculator.isActiveDay(s, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `last on-day is active`() {
        val s = schedule(daysOn = 4, daysOff = 3)
        // day 3 (0-indexed) = Jan 4
        assertTrue(CycleCalculator.isActiveDay(s, LocalDate.of(2026, 1, 4)))
    }

    @Test
    fun `first off-day is inactive`() {
        val s = schedule(daysOn = 4, daysOff = 3)
        // day 4 = Jan 5
        assertFalse(CycleCalculator.isActiveDay(s, LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `last off-day is inactive`() {
        val s = schedule(daysOn = 4, daysOff = 3)
        // day 6 = Jan 7
        assertFalse(CycleCalculator.isActiveDay(s, LocalDate.of(2026, 1, 7)))
    }

    @Test
    fun `next cycle starts active again`() {
        val s = schedule(daysOn = 4, daysOff = 3)
        // day 7 = Jan 8, new cycle
        assertTrue(CycleCalculator.isActiveDay(s, LocalDate.of(2026, 1, 8)))
    }

    @Test
    fun `date before cycle start is inactive`() {
        val s = schedule(cycleStart = LocalDate.of(2026, 3, 1))
        assertFalse(CycleCalculator.isActiveDay(s, LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `cycle with 0 off-days is always active`() {
        val s = schedule(daysOn = 5, daysOff = 0)
        assertTrue(CycleCalculator.isActiveDay(s, LocalDate.of(2026, 6, 15)))
    }

    // ── nextAlarmTime ──

    @Test
    fun `returns null when disabled`() {
        val s = schedule(enabled = false)
        assertNull(CycleCalculator.nextAlarmTime(s, LocalDateTime.of(2026, 1, 1, 8, 0)))
    }

    @Test
    fun `alarm at window start on active day`() {
        val s = schedule()
        val from = LocalDateTime.of(2026, 1, 1, 8, 0) // before 09:00
        val next = CycleCalculator.nextAlarmTime(s, from)
        assertEquals(LocalDateTime.of(2026, 1, 1, 9, 0), next)
    }

    @Test
    fun `snaps to next interval if mid-window`() {
        val s = schedule(intervalMinutes = 60)
        val from = LocalDateTime.of(2026, 1, 1, 10, 30)
        val next = CycleCalculator.nextAlarmTime(s, from)
        assertEquals(LocalDateTime.of(2026, 1, 1, 11, 0), next)
    }

    @Test
    fun `returns exact interval boundary if on it`() {
        val s = schedule(intervalMinutes = 60)
        val from = LocalDateTime.of(2026, 1, 1, 11, 0)
        val next = CycleCalculator.nextAlarmTime(s, from)
        assertEquals(LocalDateTime.of(2026, 1, 1, 11, 0), next)
    }

    @Test
    fun `skips to next active day when window passed`() {
        val s = schedule()
        val from = LocalDateTime.of(2026, 1, 1, 17, 30) // after 17:00
        val next = CycleCalculator.nextAlarmTime(s, from)
        // Jan 2 is day 1, still active
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 0), next)
    }

    @Test
    fun `skips off-days to reach next on-day`() {
        val s = schedule(daysOn = 4, daysOff = 3)
        // Jan 5 is first off-day
        val from = LocalDateTime.of(2026, 1, 5, 12, 0)
        val next = CycleCalculator.nextAlarmTime(s, from)
        // Next on-day is Jan 8
        assertEquals(LocalDateTime.of(2026, 1, 8, 9, 0), next)
    }

    @Test
    fun `30 minute interval produces correct grid`() {
        val s = schedule(intervalMinutes = 30)
        val from = LocalDateTime.of(2026, 1, 1, 9, 15)
        val next = CycleCalculator.nextAlarmTime(s, from)
        assertEquals(LocalDateTime.of(2026, 1, 1, 9, 30), next)
    }

    @Test
    fun `last possible alarm is before window end`() {
        val s = schedule(intervalMinutes = 60, windowEnd = LocalTime.of(17, 0))
        val from = LocalDateTime.of(2026, 1, 1, 16, 0)
        val next = CycleCalculator.nextAlarmTime(s, from)
        assertEquals(LocalDateTime.of(2026, 1, 1, 16, 0), next)
    }

    @Test
    fun `no alarm at exact window end`() {
        val s = schedule(intervalMinutes = 60, windowEnd = LocalTime.of(17, 0))
        val from = LocalDateTime.of(2026, 1, 1, 16, 30)
        val next = CycleCalculator.nextAlarmTime(s, from)
        // 17:00 is not before windowEnd (17:00), so skips to next day
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 0), next)
    }

    @Test
    fun `far future still finds an alarm`() {
        val s = schedule()
        val from = LocalDateTime.of(2026, 1, 6, 12, 0) // off-day
        val next = CycleCalculator.nextAlarmTime(s, from)
        assertNotNull(next)
        assertTrue(CycleCalculator.isActiveDay(s, next!!.toLocalDate()))
    }
}
