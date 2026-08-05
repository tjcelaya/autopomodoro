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
    private const val ACTION_REFRESH_STATUS = "com.tjcelaya.autopomodoro.ACTION_REFRESH_STATUS"
    private const val EXTRA_SCHEDULE_ID = "schedule_id"

    /**
     * Request codes for the status-refresh [PendingIntent] live in their own band so they can
     * never collide with the alarm [PendingIntent] for the same schedule id (which uses the
     * id itself as its request code).
     */
    private const val STATUS_REFRESH_REQUEST_CODE_OFFSET = 2_000_000

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

        // A schedule that will never fire again must not keep a status notification alive. The
        // notification is otherwise only ever revisited when an alarm fires, so without this it
        // survives being switched off — still on screen, describing a cycle that is not running.
        if (!schedule.isEnabled) {
            StatusNotifier.clear(context, schedule.id)
            return
        }

        val next = CycleCalculator.nextAlarmTime(schedule, LocalDateTime.now())
        if (next == null) {
            StatusNotifier.clear(context, schedule.id)
            return
        }
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    /**
     * Cancel an alarm for a specific schedule ID, and tear down its status notification with it.
     *
     * This is the deletion path, so the notification has to go too — nothing else will ever
     * revisit it once the row is gone.
     */
    fun cancel(context: Context, scheduleId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntentFor(context, scheduleId))
        StatusNotifier.clear(context, scheduleId)
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
     * Schedules the one-shot alarm that re-posts the status notification for [scheduleId] at
     * [at] (see [StatusUpdateReceiver]), so its relative duration stays accurate between real
     * alarms. Going through [AlarmManager] rather than an in-process timer is what lets it
     * survive process death without depending on anything else waking the app up.
     *
     * Deliberately inexact, unlike the alarms themselves. A refresh only rewrites text that is
     * already on screen, so drifting by a few minutes under Doze costs nothing — and
     * [AlarmManager.setExactAndAllowWhileIdle] is a rate-limited, budgeted resource that should
     * be spent on alarms the user actually asked for.
     */
    fun scheduleStatusRefresh(context: Context, scheduleId: Int, at: LocalDateTime) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = statusRefreshPendingIntentFor(context, scheduleId)
        val triggerAtMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    /** Cancel a pending status-refresh alarm for [scheduleId], if any is scheduled. */
    fun cancelStatusRefresh(context: Context, scheduleId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(statusRefreshPendingIntentFor(context, scheduleId))
    }

    private fun statusRefreshPendingIntentFor(context: Context, scheduleId: Int): PendingIntent {
        val intent = Intent(context, StatusUpdateReceiver::class.java).apply {
            action = ACTION_REFRESH_STATUS
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            STATUS_REFRESH_REQUEST_CODE_OFFSET + scheduleId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
