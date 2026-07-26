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
    private const val EXTRA_SCHEDULE_ID = "schedule_id"

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
}
