package com.tjcelaya.autopomodoro.scheduler

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tjcelaya.autopomodoro.R
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.util.DurationFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure text/icon composition for the notification layer.
 *
 * [AlarmReceiver] needs a live Android [android.content.Context] to actually post a
 * notification, which makes it impossible to exercise with a fast JVM unit test. Every
 * decision that determines *what* gets shown — which icon, which string resource, which
 * format arguments — is made here instead, in a plain Kotlin object with no Android
 * dependency, so it can be covered by `app/src/test`.
 *
 * ## Why resource ids + format args, not pre-built strings
 *
 * Two ways to keep this object Android-free were on the table:
 *   1. Return `@StringRes` ids plus plain format arguments, and let the caller (which does
 *      have a `Context`) resolve them with `context.getString(id, *args)`.
 *   2. Inject string templates (e.g. plain `String.format` patterns) into this object so it
 *      can build the final sentence itself, entirely independent of `strings.xml`.
 *
 * This picks (1). The PR requirement is that user-facing text lives in `strings.xml` — that
 * only works cleanly if `strings.xml` stays the single source of truth for wording, which
 * means resolution has to happen against real Android `Resources` (for pluralization,
 * locale-correct `%s` handling, RTL, etc). Option (2) would need a second, parallel copy of
 * the templates just for tests, and the two could drift. With option (1), tests assert on the
 * resource id (so a copy-editing change to the sentence can't silently break a test) and on
 * the exact values fed into each placeholder (the formatted time/duration/date strings), which
 * is exactly the part of this class that has real logic.
 */
object NotificationContent {

    /** `h:mm a`, e.g. "9:00 AM" — the absolute half of every "at X (in Y)" pairing. */
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /** `MMM d`, e.g. "Jan 8" — used only for "the cycle will return on <date>". */
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    /** Offset added to a schedule's id to get its persistent status notification's id, so it
     * never collides with the transient per-alarm notification, which is keyed directly on
     * the schedule id. */
    private const val STATUS_NOTIFICATION_ID_OFFSET = 1_000_000

    fun statusNotificationId(scheduleId: Int): Int = STATUS_NOTIFICATION_ID_OFFSET + scheduleId

    /** Content for the transient, high-priority notification posted when an alarm fires. */
    data class AlarmContent(
        @DrawableRes val iconRes: Int,
        @StringRes val textRes: Int,
        val textArgs: List<Any>,
    )

    /**
     * Content for the persistent, low-priority status notification, or `null` when no status
     * should be shown at all ([CyclePhase.Inactive]) — the caller should cancel any existing
     * status notification for the schedule in that case.
     */
    data class StatusContent(
        @DrawableRes val iconRes: Int,
        @StringRes val textRes: Int,
        val textArgs: List<Any>,
    )

    /**
     * Builds the transient notification shown for the alarm that just fired at [firedAt].
     *
     * When it was the day's last alarm ([CycleCalculator.isLastAlarmOfDay]), this gets a
     * distinct icon and says so, appending whether the cycle picks back up tomorrow
     * ([CycleCalculator.repeatsTomorrow]) or only after an off-day block
     * ([CycleCalculator.nextActiveDate]). Otherwise it names the next alarm later today, both
     * as an absolute time and a compact relative duration.
     */
    fun forAlarmFired(schedule: PomodoroSchedule, firedAt: LocalDateTime): AlarmContent {
        if (!CycleCalculator.isLastAlarmOfDay(schedule, firedAt)) {
            // isLastAlarmOfDay being false guarantees another slot remains today; the +1
            // second search start just excludes firedAt itself from the candidates.
            val next = CycleCalculator.nextAlarmTime(schedule, firedAt.plusSeconds(1)) ?: firedAt
            return AlarmContent(
                iconRes = R.drawable.ic_notif_alarm,
                textRes = R.string.notif_alarm_text_next,
                textArgs = listOf(TIME_FORMAT.format(next), DurationFormat.between(firedAt, next)),
            )
        }

        val today = firedAt.toLocalDate()
        return if (CycleCalculator.repeatsTomorrow(schedule, today)) {
            AlarmContent(
                iconRes = R.drawable.ic_notif_last_alarm,
                textRes = R.string.notif_alarm_text_last_repeats,
                textArgs = emptyList(),
            )
        } else {
            val returnDate = CycleCalculator.nextActiveDate(schedule, today.plusDays(1))
            AlarmContent(
                iconRes = R.drawable.ic_notif_last_alarm,
                textRes = R.string.notif_alarm_text_last_returns,
                textArgs = listOf(returnDate?.let(DATE_FORMAT::format).orEmpty()),
            )
        }
    }

    /**
     * Builds the persistent status notification for [phase] as observed at [now].
     *
     * Returns `null` for [CyclePhase.Inactive] — there is nothing useful to say, so the
     * caller should cancel any status notification currently showing for the schedule instead
     * of posting one.
     */
    fun forStatus(phase: CyclePhase, now: LocalDateTime): StatusContent? = when (phase) {
        is CyclePhase.Active -> StatusContent(
            iconRes = R.drawable.ic_notif_status_active,
            textRes = R.string.notif_status_text_active,
            textArgs = listOf(
                TIME_FORMAT.format(phase.nextAlarm),
                DurationFormat.between(now, phase.nextAlarm),
            ),
        )

        is CyclePhase.Cooldown -> StatusContent(
            iconRes = R.drawable.ic_notif_status_cooldown,
            textRes = R.string.notif_status_text_cooldown,
            textArgs = listOf(
                TIME_FORMAT.format(phase.cooldownEndsAt),
                TIME_FORMAT.format(phase.resumesAt),
                DurationFormat.between(now, phase.resumesAt),
            ),
        )

        is CyclePhase.Waiting -> StatusContent(
            iconRes = R.drawable.ic_notif_status_waiting,
            textRes = R.string.notif_status_text_waiting,
            textArgs = listOf(
                TIME_FORMAT.format(phase.resumesAt),
                DurationFormat.between(now, phase.resumesAt),
            ),
        )

        CyclePhase.Inactive -> null
    }

    /**
     * When the status notification should next be re-posted so its relative duration does not
     * go stale, or `null` if there is nothing left to refresh ([CyclePhase.Inactive], or a
     * phase whose boundary has already passed).
     *
     * The status notification is otherwise only rebuilt when an alarm fires, so during a long
     * cooldown or an off-day block its "in 3d2h10m" drifts arbitrarily far from the truth while
     * the absolute time beside it stays correct — a visibly self-contradictory notification.
     *
     * The cadence is proportional to how much time is actually left, because that is what
     * decides when the rendered string next changes. [DurationFormat] drops zero units, so at a
     * three-day remove the text only moves once an hour, while in the last quarter-hour it
     * moves every minute. Refreshing on a flat one-minute tick would post ~4,300 redundant
     * updates across a three-day wait to catch the handful that change anything; this posts a
     * few dozen. The result is clamped to the phase's own boundary so a refresh never lands
     * after the event that supersedes it.
     */
    fun nextStatusRefresh(phase: CyclePhase, now: LocalDateTime): LocalDateTime? {
        val target = phase.nextAlarmOrNull ?: return null
        val boundary = when (phase) {
            is CyclePhase.Active -> phase.nextAlarm
            // The cooldown lapsing is what changes this notification next, not the alarm it is
            // counting down to — that is generally days later.
            is CyclePhase.Cooldown -> phase.cooldownEndsAt
            is CyclePhase.Waiting -> phase.resumesAt
            CyclePhase.Inactive -> return null
        }
        if (!boundary.isAfter(now)) return null

        val candidate = now.plus(refreshStep(Duration.between(now, target)))
        return if (candidate.isBefore(boundary)) candidate else boundary
    }

    /** How long to wait before the next status refresh, given [remaining] until the alarm the
     * notification is counting down to. Mirrors [DurationFormat]'s granularity. */
    private fun refreshStep(remaining: Duration): Duration = when {
        remaining > Duration.ofHours(24) -> Duration.ofHours(1)
        remaining > Duration.ofHours(2) -> Duration.ofMinutes(15)
        remaining > Duration.ofMinutes(15) -> Duration.ofMinutes(5)
        else -> Duration.ofMinutes(1)
    }
}
