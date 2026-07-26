package com.tjcelaya.autopomodoro.scheduler

import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Pure, stateless helper that answers scheduling questions about a [PomodoroSchedule].
 */
object CycleCalculator {

    /**
     * Returns true if [date] falls within an "on" window of the schedule's cycle.
     *
     * The cycle repeats every (daysOn + daysOff) days starting from [PomodoroSchedule.cycleStartDate].
     * Days 0..<daysOn are "on"; the rest are "off".
     * Dates before the cycle start are treated as "off".
     */
    fun isActiveDay(schedule: PomodoroSchedule, date: LocalDate): Boolean {
        val daysSinceStart = ChronoUnit.DAYS.between(schedule.cycleStartDate, date)
        if (daysSinceStart < 0) return false
        val cycleLength = schedule.daysOn + schedule.daysOff
        if (cycleLength <= 0) return false
        val positionInCycle = (daysSinceStart % cycleLength).toInt()
        return positionInCycle < schedule.daysOn
    }

    /**
     * Computes the next alarm time at or after [from] for the given schedule.
     *
     * Returns `null` when the schedule is disabled or no valid time can be found within a
     * reasonable search horizon (one full cycle length, minimum 60 days).
     */
    fun nextAlarmTime(schedule: PomodoroSchedule, from: LocalDateTime): LocalDateTime? {
        if (!schedule.isEnabled) return null
        val cycleLength = schedule.daysOn + schedule.daysOff
        if (cycleLength <= 0 || schedule.intervalMinutes <= 0) return null

        val maxDaysToSearch = maxOf(cycleLength * 2, 60)
        var currentDate = from.toLocalDate()
        val endDate = currentDate.plusDays(maxDaysToSearch.toLong())

        while (!currentDate.isAfter(endDate)) {
            if (isActiveDay(schedule, currentDate)) {
                val candidate = nextTimeInWindow(schedule, currentDate, from)
                if (candidate != null) return candidate
            }
            // Move to next day, starting at the window start time
            currentDate = currentDate.plusDays(1)
        }
        return null
    }

    /**
     * For a specific active [date], find the next alarm time within the daily window
     * that is at or after [from].
     *
     * Returns `null` when no such time exists on this day (i.e. the window has already passed).
     */
    private fun nextTimeInWindow(
        schedule: PomodoroSchedule,
        date: LocalDate,
        from: LocalDateTime,
    ): LocalDateTime? {
        val windowOpen = LocalDateTime.of(date, schedule.windowStart)
        val windowClose = LocalDateTime.of(date, schedule.windowEnd)

        // Determine the earliest candidate on this day
        val earliest = if (from.isAfter(windowOpen)) from else windowOpen

        if (!earliest.isBefore(windowClose)) return null // window already passed

        // Snap to the interval grid anchored at windowStart
        val minutesSinceWindowStart = ChronoUnit.MINUTES.between(windowOpen, earliest)
        val intervalsPassed = minutesSinceWindowStart / schedule.intervalMinutes
        var candidate = windowOpen.plusMinutes(intervalsPassed * schedule.intervalMinutes)
        if (candidate.isBefore(earliest)) {
            candidate = candidate.plusMinutes(schedule.intervalMinutes.toLong())
        }

        return if (candidate.isBefore(windowClose)) candidate else null
    }

    // ── Cycle phase ──

    /**
     * Returns the next date at or after [from] that falls in an "on" window, or `null` when
     * none exists within the search horizon.
     *
     * Unlike [nextAlarmTime] this jumps straight to [PomodoroSchedule.cycleStartDate] when
     * [from] precedes it, so a cycle starting well beyond the horizon is still found.
     */
    fun nextActiveDate(schedule: PomodoroSchedule, from: LocalDate): LocalDate? {
        val cycleLength = schedule.daysOn + schedule.daysOff
        if (cycleLength <= 0 || schedule.daysOn <= 0) return null

        var date = if (from.isBefore(schedule.cycleStartDate)) schedule.cycleStartDate else from
        repeat(cycleLength + 1) {
            if (isActiveDay(schedule, date)) return date
            date = date.plusDays(1)
        }
        return null
    }

    /**
     * Returns true when no further alarm is due on [alarmTime]'s own day — i.e. the alarm that
     * just fired at [alarmTime] was the day's last.
     *
     * Mirrors the rule in [nextTimeInWindow]: a slot counts only while it falls strictly
     * before the window's end.
     */
    fun isLastAlarmOfDay(schedule: PomodoroSchedule, alarmTime: LocalDateTime): Boolean {
        if (schedule.intervalMinutes <= 0) return true
        if (!isActiveDay(schedule, alarmTime.toLocalDate())) return false

        val windowClose = LocalDateTime.of(alarmTime.toLocalDate(), schedule.windowEnd)
        val following = alarmTime.plusMinutes(schedule.intervalMinutes.toLong())
        return !following.isBefore(windowClose)
    }

    /**
     * Returns true when the day after [date] is also an active day, so a cycle ending on
     * [date] simply picks back up tomorrow rather than pausing for off-days.
     */
    fun repeatsTomorrow(schedule: PomodoroSchedule, date: LocalDate): Boolean =
        isActiveDay(schedule, date.plusDays(1))

    /**
     * The most recent alarm at or before [at], or `null` when none has fired yet.
     */
    fun lastAlarmTime(schedule: PomodoroSchedule, at: LocalDateTime): LocalDateTime? {
        if (!schedule.isEnabled) return null
        val cycleLength = schedule.daysOn + schedule.daysOff
        if (cycleLength <= 0 || schedule.intervalMinutes <= 0) return null

        var date = at.toLocalDate()
        repeat(cycleLength + 1) {
            if (date.isBefore(schedule.cycleStartDate)) return null
            if (isActiveDay(schedule, date)) {
                lastTimeInWindow(schedule, date, at)?.let { return it }
            }
            date = date.minusDays(1)
        }
        return null
    }

    /**
     * The latest alarm slot on [date] that is at or before [at], or `null` when [at] precedes
     * the window or the window holds no slots.
     */
    private fun lastTimeInWindow(
        schedule: PomodoroSchedule,
        date: LocalDate,
        at: LocalDateTime,
    ): LocalDateTime? {
        val windowOpen = LocalDateTime.of(date, schedule.windowStart)
        val windowClose = LocalDateTime.of(date, schedule.windowEnd)
        if (at.isBefore(windowOpen)) return null

        val windowMinutes = ChronoUnit.MINUTES.between(windowOpen, windowClose)
        if (windowMinutes <= 0) return null

        // Highest k with k*interval < windowMinutes
        val lastSlot = (windowMinutes - 1) / schedule.intervalMinutes
        val lastPossible = windowOpen.plusMinutes(lastSlot * schedule.intervalMinutes)

        val capped = if (at.isBefore(lastPossible)) at else lastPossible
        val elapsed = ChronoUnit.MINUTES.between(windowOpen, capped)
        return windowOpen.plusMinutes((elapsed / schedule.intervalMinutes) * schedule.intervalMinutes)
    }

    /**
     * Classifies where [schedule] sits at [now].
     *
     * [CyclePhase.Cooldown] is only ever returned when the schedule has a positive
     * [PomodoroSchedule.cooldownMinutes] and the window it defines has not yet lapsed.
     */
    fun phaseAt(schedule: PomodoroSchedule, now: LocalDateTime): CyclePhase {
        if (!schedule.isEnabled) return CyclePhase.Inactive

        val next = nextAlarmTime(schedule, now) ?: return CyclePhase.Inactive
        if (next.toLocalDate() == now.toLocalDate()) return CyclePhase.Active(next)

        val cooldown = schedule.cooldownMinutes
        if (cooldown != null && cooldown > 0) {
            val last = lastAlarmTime(schedule, now)
            if (last != null) {
                val endsAt = last.plusMinutes(cooldown.toLong())
                if (now.isBefore(endsAt)) {
                    return CyclePhase.Cooldown(resumesAt = next, cooldownEndsAt = endsAt)
                }
            }
        }

        return CyclePhase.Waiting(resumesAt = next)
    }
}
