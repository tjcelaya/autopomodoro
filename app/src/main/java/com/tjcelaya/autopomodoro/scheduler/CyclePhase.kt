package com.tjcelaya.autopomodoro.scheduler

import java.time.LocalDateTime

/**
 * Where a schedule sits in its cycle at a given moment.
 *
 * This is the contract the notification layer reads to decide which icon and wording to
 * show, so that no scheduling decisions have to be made inside [android.content.BroadcastReceiver]
 * code that cannot be unit tested.
 */
sealed interface CyclePhase {

    /** At least one more alarm is due later today. */
    data class Active(val nextAlarm: LocalDateTime) : CyclePhase

    /**
     * The day's last alarm has already fired and the configured cooldown is still running.
     *
     * [cooldownEndsAt] is when the cooldown lapses; [resumesAt] is the next alarm after that,
     * which is generally on a later day.
     */
    data class Cooldown(
        val resumesAt: LocalDateTime,
        val cooldownEndsAt: LocalDateTime,
    ) : CyclePhase

    /**
     * Nothing more is due today and no cooldown is running — either the day's window has
     * closed, or today is an off-day, or the cycle has not started yet.
     */
    data class Waiting(val resumesAt: LocalDateTime) : CyclePhase

    /** The schedule is disabled, or it has no reachable future alarm. */
    data object Inactive : CyclePhase

    /** The next alarm this phase is waiting on, if any. */
    val nextAlarmOrNull: LocalDateTime?
        get() = when (this) {
            is Active -> nextAlarm
            is Cooldown -> resumesAt
            is Waiting -> resumesAt
            Inactive -> null
        }
}
