package com.tjcelaya.autopomodoro.scheduler

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tjcelaya.autopomodoro.AutopomodoroApp
import com.tjcelaya.autopomodoro.MainActivity
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import java.time.LocalDateTime

/**
 * Single owner of the persistent status notification: posting it, clearing it, and keeping its
 * refresh alarm in step with the phase it is showing.
 *
 * This used to live inside [AlarmReceiver], which meant the notification was only ever touched
 * when an alarm fired. Everything that can invalidate it — a schedule being disabled or
 * deleted, a cooldown lapsing, the relative text ageing — happens at some *other* moment, so
 * the lifecycle is centralised here and driven from three places: [AlarmReceiver] when an alarm
 * fires, [StatusUpdateReceiver] when a refresh alarm fires, and [AlarmSchedulerService] when a
 * schedule stops being scheduled at all.
 *
 * As with [AlarmReceiver], every content decision is delegated to the pure [NotificationContent]
 * so it stays unit-testable; this object only resolves resources against a real [Context].
 */
object StatusNotifier {

    /** Request codes for the status notification's content [PendingIntent] live in their own
     * band so they never collide with the transient alarm notification's content intent, which
     * uses the schedule id directly. */
    private const val CONTENT_REQUEST_CODE_OFFSET = 3_000_000

    /**
     * Recomputes the phase for [schedule] at [now] and posts, updates or clears its status
     * notification accordingly, then arms the next refresh.
     */
    fun update(context: Context, schedule: PomodoroSchedule, now: LocalDateTime) {
        val phase = CycleCalculator.phaseAt(schedule, now)
        val content = NotificationContent.forStatus(phase, now)

        if (content == null) {
            clear(context, schedule.id)
            return
        }

        // Clearing works without the runtime permission, so it is handled above; only posting
        // needs the check. Bailing out here leaves any existing notification alone, which is
        // the best available outcome — we cannot replace it and it is still swipeable.
        if (!canPost(context)) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SCHEDULE_ID, schedule.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE_OFFSET + schedule.id,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, AutopomodoroApp.STATUS_CHANNEL_ID)
            .setSmallIcon(content.iconRes)
            .setContentTitle(schedule.name)
            .setContentText(context.getString(content.textRes, *content.textArgs.toTypedArray()))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Deliberately NOT setOngoing(true): an ongoing notification cannot be swiped away,
            // and the only automatic dismissal was wired to the cooldown phase, so a schedule
            // with no cooldown left the user with a permanently undismissable notification.
            // It stays until dismissed either way; this just lets the user do it.
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NotificationContent.statusNotificationId(schedule.id), notification)

        val refreshAt = NotificationContent.nextStatusRefresh(phase, now)
        if (refreshAt != null) {
            AlarmSchedulerService.scheduleStatusRefresh(context, schedule.id, refreshAt)
        } else {
            AlarmSchedulerService.cancelStatusRefresh(context, schedule.id)
        }
    }

    /**
     * Removes the status notification for [scheduleId] and drops its pending refresh alarm.
     *
     * Safe to call for a schedule that has already been deleted from the database — both calls
     * are no-ops when nothing is posted or scheduled.
     */
    fun clear(context: Context, scheduleId: Int) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationContent.statusNotificationId(scheduleId))
        AlarmSchedulerService.cancelStatusRefresh(context, scheduleId)
    }

    private fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
