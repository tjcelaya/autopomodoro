package com.tjcelaya.autopomodoro.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tjcelaya.autopomodoro.data.AppDatabase
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules (or cancels) the single next exact alarm for each enabled [PomodoroSchedule]
 * via [AlarmManager].
 */
object AlarmSchedulerService {

    private const val ACTION_ALARM = "com.tjcelaya.autopomodoro.ACTION_ALARM"
    private const val ACTION_DISMISS_STATUS = "com.tjcelaya.autopomodoro.ACTION_DISMISS_STATUS"
    private const val EXTRA_SCHEDULE_ID = "schedule_id"

    /**
     * Request codes for the status-dismiss [PendingIntent] live in their own band so they can
     * never collide with the alarm [PendingIntent] for the same schedule id (which uses the
     * id itself as its request code).
     */
    private const val STATUS_DISMISS_REQUEST_CODE_OFFSET = 2_000_000

    /** Re-schedule alarms for every enabled schedule. */
    suspend fun rescheduleAll(context: Context) {
        val dao = AppDatabase.getInstance(context).scheduleDao()
        dao.getEnabled().forEach { schedule ->
            scheduleNext(context, schedule)
        }
    }

    /** Schedule (or cancel) the next alarm for a single schedule. */
    fun scheduleNext(context: Context, schedule: PomodoroSchedule) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntentFor(context, schedule.id)

        // Always cancel any existing alarm first
        alarmManager.cancel(pendingIntent)

        if (!schedule.isEnabled) return

        val next = CycleCalculator.nextAlarmTime(schedule, LocalDateTime.now()) ?: return
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    /** Cancel an alarm for a specific schedule ID. */
    fun cancel(context: Context, scheduleId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntentFor(context, scheduleId))
    }

    fun pendingIntentFor(context: Context, scheduleId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            scheduleId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun extractScheduleId(intent: Intent): Int =
        intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)

    /**
     * Schedules a one-shot alarm that dismisses the persistent status notification for
     * [scheduleId] once its cooldown lapses at [at]. This is how the status notification's
     * auto-dismiss (see [StatusDismissReceiver]) survives process death and doesn't depend on
     * anything else waking the app up in the meantime.
     */
    fun scheduleStatusDismiss(context: Context, scheduleId: Int, at: LocalDateTime) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = statusDismissPendingIntentFor(context, scheduleId)
        val triggerAtMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    /** Cancel a pending status-dismiss alarm for [scheduleId], if any is scheduled. */
    fun cancelStatusDismiss(context: Context, scheduleId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(statusDismissPendingIntentFor(context, scheduleId))
    }

    private fun statusDismissPendingIntentFor(context: Context, scheduleId: Int): PendingIntent {
        val intent = Intent(context, StatusDismissReceiver::class.java).apply {
            action = ACTION_DISMISS_STATUS
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            STATUS_DISMISS_REQUEST_CODE_OFFSET + scheduleId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
