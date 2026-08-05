package com.tjcelaya.autopomodoro

import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.scheduler.CyclePhase
import com.tjcelaya.autopomodoro.scheduler.NotificationContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Covers [NotificationContent] — the pure object that decides icon + string-resource + format
 * args for both the transient per-alarm notification and the persistent status notification.
 * This is the only part of the notification feature that can run as a fast JVM test; the
 * receivers themselves need a live Android [android.content.Context] and are exercised
 * manually / on-device instead.
 *
 * Same 4-on/3-off schedule as [CyclePhaseTest]: cycle starts Thu 2026-01-01, daily window
 * 09:00-17:00, hourly interval, so the day's slots are 09:00..16:00 and 16:00 is the last.
 */
class NotificationContentTest {

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

    // ── forAlarmFired: not the last alarm of the day ──

    @Test
    fun `mid-day alarm names the next slot with absolute and relative time`() {
        val s = schedule()
        val content = NotificationContent.forAlarmFired(s, LocalDateTime.of(2026, 1, 1, 10, 0))

        assertEquals(R.string.notif_alarm_text_next, content.textRes)
        assertEquals(listOf("11:00 AM", "1h"), content.textArgs)
    }

    @Test
    fun `first alarm of the day names the second slot`() {
        val s = schedule()
        val content = NotificationContent.forAlarmFired(s, LocalDateTime.of(2026, 1, 1, 9, 0))

        assertEquals(listOf("10:00 AM", "1h"), content.textArgs)
    }

    @Test
    fun `half-hour interval reports the correct next slot and gap`() {
        val s = schedule(intervalMinutes = 30)
        val content = NotificationContent.forAlarmFired(s, LocalDateTime.of(2026, 1, 1, 16, 0))

        // 16:00 is not last when the interval is 30 (16:30 still fits before 17:00 close)
        assertEquals(R.string.notif_alarm_text_next, content.textRes)
        assertEquals(listOf("4:30 PM", "30m"), content.textArgs)
    }

    // ── forAlarmFired: last alarm of the day, cycle repeats tomorrow ──

    @Test
    fun `last alarm mid-block gets the last-alarm icon and repeats-tomorrow text`() {
        val s = schedule()
        val content = NotificationContent.forAlarmFired(s, LocalDateTime.of(2026, 1, 1, 16, 0))

        assertEquals(R.drawable.ic_notif_last_alarm, content.iconRes)
        assertEquals(R.string.notif_alarm_text_last_repeats, content.textRes)
        assertTrue(content.textArgs.isEmpty())
    }

    // ── forAlarmFired: last alarm of the day, cycle pauses for the off-block ──

    @Test
    fun `last alarm of the on-block names the date the cycle returns`() {
        val s = schedule()
        // Jan 4 is the final on-day; Jan 5-7 are off; the cycle resumes Jan 8.
        val content = NotificationContent.forAlarmFired(s, LocalDateTime.of(2026, 1, 4, 16, 0))

        assertEquals(R.drawable.ic_notif_last_alarm, content.iconRes)
        assertEquals(R.string.notif_alarm_text_last_returns, content.textRes)
        assertEquals(listOf("Jan 8"), content.textArgs)
    }

    @Test
    fun `last-alarm icon differs from the plain alarm icon`() {
        val s = schedule()
        val plain = NotificationContent.forAlarmFired(s, LocalDateTime.of(2026, 1, 1, 10, 0))
        val last = NotificationContent.forAlarmFired(s, LocalDateTime.of(2026, 1, 1, 16, 0))

        assertNotEquals(plain.iconRes, last.iconRes)
    }

    // ── forStatus: Active ──

    @Test
    fun `active status names the next alarm today`() {
        val phase = CyclePhase.Active(LocalDateTime.of(2026, 1, 1, 11, 0))
        val content = NotificationContent.forStatus(phase, LocalDateTime.of(2026, 1, 1, 10, 30))

        requireNotNull(content)
        assertEquals(R.drawable.ic_notif_status_active, content.iconRes)
        assertEquals(R.string.notif_status_text_active, content.textRes)
        assertEquals(listOf("11:00 AM", "30m"), content.textArgs)
    }

    // ── forStatus: Cooldown ──

    @Test
    fun `cooldown status names both the cooldown end and the resuming alarm`() {
        val phase = CyclePhase.Cooldown(
            resumesAt = LocalDateTime.of(2026, 1, 2, 9, 0),
            cooldownEndsAt = LocalDateTime.of(2026, 1, 1, 17, 0),
        )
        val content = NotificationContent.forStatus(phase, LocalDateTime.of(2026, 1, 1, 16, 30))

        requireNotNull(content)
        assertEquals(R.drawable.ic_notif_status_cooldown, content.iconRes)
        assertEquals(R.string.notif_status_text_cooldown, content.textRes)
        assertEquals(listOf("5:00 PM", "9:00 AM", "16h30m"), content.textArgs)
    }

    // ── forStatus: Waiting ──

    @Test
    fun `waiting status names the resuming alarm`() {
        val phase = CyclePhase.Waiting(LocalDateTime.of(2026, 1, 2, 9, 0))
        val content = NotificationContent.forStatus(phase, LocalDateTime.of(2026, 1, 1, 18, 0))

        requireNotNull(content)
        assertEquals(R.drawable.ic_notif_status_waiting, content.iconRes)
        assertEquals(R.string.notif_status_text_waiting, content.textRes)
        assertEquals(listOf("9:00 AM", "15h"), content.textArgs)
    }

    @Test
    fun `waiting status caps the relative time at the imminent marker when overdue`() {
        // Defensive case: 'now' somehow already past the recorded resumesAt.
        val phase = CyclePhase.Waiting(LocalDateTime.of(2026, 1, 2, 9, 0))
        val content = NotificationContent.forStatus(phase, LocalDateTime.of(2026, 1, 2, 9, 5))

        requireNotNull(content)
        assertEquals("<1m", content.textArgs[1])
    }

    // ── forStatus: Inactive ──

    @Test
    fun `inactive phase has no status content`() {
        val content = NotificationContent.forStatus(CyclePhase.Inactive, LocalDateTime.of(2026, 1, 1, 12, 0))
        assertNull(content)
    }

    // ── status icons are all mutually distinct ──

    @Test
    fun `active, cooldown and waiting status icons are all different`() {
        val active = NotificationContent.forStatus(
            CyclePhase.Active(LocalDateTime.of(2026, 1, 1, 11, 0)),
            LocalDateTime.of(2026, 1, 1, 10, 0),
        )!!
        val cooldown = NotificationContent.forStatus(
            CyclePhase.Cooldown(
                resumesAt = LocalDateTime.of(2026, 1, 2, 9, 0),
                cooldownEndsAt = LocalDateTime.of(2026, 1, 1, 17, 0),
            ),
            LocalDateTime.of(2026, 1, 1, 16, 30),
        )!!
        val waiting = NotificationContent.forStatus(
            CyclePhase.Waiting(LocalDateTime.of(2026, 1, 2, 9, 0)),
            LocalDateTime.of(2026, 1, 1, 18, 0),
        )!!

        val icons = setOf(active.iconRes, cooldown.iconRes, waiting.iconRes)
        assertEquals(3, icons.size)
    }

    // ── statusNotificationId ──

    @Test
    fun `status notification id is offset from the schedule id`() {
        assertEquals(1_000_005, NotificationContent.statusNotificationId(5))
        assertEquals(1_000_000, NotificationContent.statusNotificationId(0))
    }

    @Test
    fun `status notification id never collides with a small schedule id`() {
        // Room's autoGenerate ids start at 1 and grow slowly; the offset must stay well clear.
        for (id in 0..1000) {
            assertNotEquals(id, NotificationContent.statusNotificationId(id))
        }
    }
}
