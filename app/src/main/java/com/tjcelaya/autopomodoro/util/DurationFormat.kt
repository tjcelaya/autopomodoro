package com.tjcelaya.autopomodoro.util

import java.time.Duration
import java.time.LocalDateTime

/**
 * Renders durations as compact relative strings for notification text — "3d2h10m", "2h",
 * "45m". Units that would read as zero are dropped, so the string carries only as much
 * detail as the duration actually needs.
 */
object DurationFormat {

    /** Shown for anything under a minute, including zero and negative durations. */
    const val IMMINENT = "<1m"

    fun compact(duration: Duration): String {
        if (duration.isNegative || duration.toMinutes() < 1) return IMMINENT

        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        return buildString {
            if (days > 0) append(days).append('d')
            if (hours > 0) append(hours).append('h')
            if (minutes > 0) append(minutes).append('m')
        }
    }

    /** Convenience for the common "how long until the next alarm" case. */
    fun between(from: LocalDateTime, to: LocalDateTime): String =
        compact(Duration.between(from, to))
}
