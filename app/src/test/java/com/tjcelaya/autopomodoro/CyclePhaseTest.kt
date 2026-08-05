package com.tjcelaya.autopomodoro

import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.scheduler.CycleCalculator
import com.tjcelaya.autopomodoro.scheduler.CyclePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Covers the cycle-phase additions: [CycleCalculator.isLastAlarmOfDay],
 * [CycleCalculator.nextActiveDate], [CycleCalculator.lastAlarmTime] and
 * [CycleCalculator.phaseAt].
 *
 * The default schedule is a 4-on/3-off cycle starting Thu 2026-01-01, firing hourly between
 * 09:00 and 17:00 — so the day's slots are 09:00..16:00 and 16:00 is the last.
 */
class CyclePhaseTest {

    private fun schedule(
        daysOn: Int = 4,
        daysOff: Int = 3,
        cycleStart: LocalDate = LocalDate.of(2026, 1, 1),
        windowStart: LocalTime = LocalTime.of(9, 0),
        windowEnd: LocalTime = LocalTime.of(17, 0),
        intervalMinutes: Int = 60,
        enabled: Boolean = true,
        cooldownMinutes: Int? = null,
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
        cooldownMinutes = cooldownMinutes,
    )

    // ── isLastAlarmOfDay ──

    @Test
    fun `final slot of the day is the last alarm`() {
        val s = schedule()
        assertTrue(CycleCalculator.isLastAlarmOfDay(s, LocalDateTime.of(2026, 1, 1, 16, 0)))
    }

    @Test
    fun `mid-window slot is not the last alarm`() {
        val s = schedule()
        assertFalse(CycleCalculator.isLastAlarmOfDay(s, LocalDateTime.of(2026, 1, 1, 15, 0)))
    }

    @Test
    fun `first slot of the day is not the last alarm`() {
        val s = schedule()
        assertFalse(CycleCalculator.isLastAlarmOfDay(s, LocalDateTime.of(2026, 1, 1, 9, 0)))
    }

    @Test
    fun `nothing on an off-day counts as the last alarm`() {
        val s = schedule()
        // Jan 5 is the first off-day
        assertFalse(CycleCalculator.isLastAlarmOfDay(s, LocalDateTime.of(2026, 1, 5, 16, 0)))
    }

    @Test
    fun `half-hour interval moves the last slot to 1630`() {
        val s = schedule(intervalMinutes = 30)
        assertFalse(CycleCalculator.isLastAlarmOfDay(s, LocalDateTime.of(2026, 1, 1, 16, 0)))
        assertTrue(CycleCalculator.isLastAlarmOfDay(s, LocalDateTime.of(2026, 1, 1, 16, 30)))
    }

    // ── nextActiveDate ──

    @Test
    fun `next active date is today when today is on`() {
        val s = schedule()
        assertEquals(
            LocalDate.of(2026, 1, 1),
            CycleCalculator.nextActiveDate(s, LocalDate.of(2026, 1, 1)),
        )
    }

    @Test
    fun `next active date skips the off-block`() {
        val s = schedule()
        assertEquals(
            LocalDate.of(2026, 1, 8),
            CycleCalculator.nextActiveDate(s, LocalDate.of(2026, 1, 5)),
        )
    }

    @Test
    fun `next active date from the final off-day is the following day`() {
        val s = schedule()
        assertEquals(
            LocalDate.of(2026, 1, 8),
            CycleCalculator.nextActiveDate(s, LocalDate.of(2026, 1, 7)),
        )
    }

    @Test
    fun `next active date jumps forward to a distant cycle start`() {
        // Well beyond the 60-day horizon that nextAlarmTime searches
        val s = schedule(cycleStart = LocalDate.of(2027, 6, 1))
        assertEquals(
            LocalDate.of(2027, 6, 1),
            CycleCalculator.nextActiveDate(s, LocalDate.of(2026, 1, 1)),
        )
    }

    @Test
    fun `next active date is null when no day is ever on`() {
        val s = schedule(daysOn = 0, daysOff = 7)
        assertNull(CycleCalculator.nextActiveDate(s, LocalDate.of(2026, 1, 1)))
    }

    // ── repeatsTomorrow ──

    @Test
    fun `cycle repeats tomorrow mid-block`() {
        val s = schedule()
        assertTrue(CycleCalculator.repeatsTomorrow(s, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `cycle does not repeat tomorrow on the last on-day`() {
        val s = schedule()
        // Jan 4 is the final on-day; Jan 5 is off
        assertFalse(CycleCalculator.repeatsTomorrow(s, LocalDate.of(2026, 1, 4)))
    }

    // ── lastAlarmTime ──

    @Test
    fun `last alarm snaps back to the enclosing slot`() {
        val s = schedule()
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 12, 0),
            CycleCalculator.lastAlarmTime(s, LocalDateTime.of(2026, 1, 1, 12, 30)),
        )
    }

    @Test
    fun `last alarm is the slot itself when exactly on it`() {
        val s = schedule()
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 12, 0),
            CycleCalculator.lastAlarmTime(s, LocalDateTime.of(2026, 1, 1, 12, 0)),
        )
    }

    @Test
    fun `last alarm after the window closes is the day's final slot`() {
        val s = schedule()
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 16, 0),
            CycleCalculator.lastAlarmTime(s, LocalDateTime.of(2026, 1, 1, 20, 0)),
        )
    }

    @Test
    fun `last alarm is null before the very first window opens`() {
        val s = schedule()
        assertNull(CycleCalculator.lastAlarmTime(s, LocalDateTime.of(2026, 1, 1, 8, 0)))
    }

    @Test
    fun `last alarm on an off-day reaches back to the previous on-day`() {
        val s = schedule()
        assertEquals(
            LocalDateTime.of(2026, 1, 4, 16, 0),
            CycleCalculator.lastAlarmTime(s, LocalDateTime.of(2026, 1, 5, 12, 0)),
        )
    }

    @Test
    fun `last alarm is null when disabled`() {
        val s = schedule(enabled = false)
        assertNull(CycleCalculator.lastAlarmTime(s, LocalDateTime.of(2026, 1, 1, 12, 0)))
    }

    // ── phaseAt ──

    @Test
    fun `phase is active when more alarms remain today`() {
        val s = schedule()
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 10, 30))
        assertEquals(CyclePhase.Active(LocalDateTime.of(2026, 1, 1, 11, 0)), phase)
    }

    @Test
    fun `phase is active exactly at the final slot`() {
        val s = schedule()
        // 16:00 is itself still due, so the day is not over yet
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 16, 0))
        assertEquals(CyclePhase.Active(LocalDateTime.of(2026, 1, 1, 16, 0)), phase)
    }

    @Test
    fun `phase is waiting once the window closes without a cooldown`() {
        val s = schedule()
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 18, 0))
        assertEquals(CyclePhase.Waiting(LocalDateTime.of(2026, 1, 2, 9, 0)), phase)
    }

    @Test
    fun `phase is cooldown while the trailing period runs`() {
        val s = schedule(cooldownMinutes = 60)
        // Last alarm was 16:00, so the cooldown covers 16:00-17:00
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 16, 30))
        assertEquals(
            CyclePhase.Cooldown(
                resumesAt = LocalDateTime.of(2026, 1, 2, 9, 0),
                cooldownEndsAt = LocalDateTime.of(2026, 1, 1, 17, 0),
            ),
            phase,
        )
    }

    @Test
    fun `phase falls back to waiting once the cooldown lapses`() {
        val s = schedule(cooldownMinutes = 60)
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 17, 30))
        assertEquals(CyclePhase.Waiting(LocalDateTime.of(2026, 1, 2, 9, 0)), phase)
    }

    @Test
    fun `cooldown resume time crosses the off-block on the last on-day`() {
        val s = schedule(cooldownMinutes = 90)
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 4, 16, 30))
        assertEquals(
            CyclePhase.Cooldown(
                resumesAt = LocalDateTime.of(2026, 1, 8, 9, 0),
                cooldownEndsAt = LocalDateTime.of(2026, 1, 4, 17, 30),
            ),
            phase,
        )
    }

    @Test
    fun `null cooldown never produces a cooldown phase`() {
        val s = schedule(cooldownMinutes = null)
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 16, 30))
        assertTrue(phase is CyclePhase.Waiting)
    }

    @Test
    fun `zero cooldown never produces a cooldown phase`() {
        val s = schedule(cooldownMinutes = 0)
        val phase = CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 16, 30))
        assertTrue(phase is CyclePhase.Waiting)
    }

    @Test
    fun `phase is inactive when disabled`() {
        val s = schedule(enabled = false, cooldownMinutes = 60)
        assertEquals(CyclePhase.Inactive, CycleCalculator.phaseAt(s, LocalDateTime.of(2026, 1, 1, 12, 0)))
    }

    @Test
    fun `nextAlarmOrNull exposes the pending alarm for every phase`() {
        val active = CyclePhase.Active(LocalDateTime.of(2026, 1, 1, 11, 0))
        val cooldown = CyclePhase.Cooldown(
            resumesAt = LocalDateTime.of(2026, 1, 2, 9, 0),
            cooldownEndsAt = LocalDateTime.of(2026, 1, 1, 17, 0),
        )
        val waiting = CyclePhase.Waiting(LocalDateTime.of(2026, 1, 2, 9, 0))

        assertEquals(LocalDateTime.of(2026, 1, 1, 11, 0), active.nextAlarmOrNull)
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 0), cooldown.nextAlarmOrNull)
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 0), waiting.nextAlarmOrNull)
        assertNull(CyclePhase.Inactive.nextAlarmOrNull)
    }
}
