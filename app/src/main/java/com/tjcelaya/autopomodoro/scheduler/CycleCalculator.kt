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
}
