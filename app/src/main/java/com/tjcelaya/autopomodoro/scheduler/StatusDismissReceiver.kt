package com.tjcelaya.autopomodoro.scheduler

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires once a schedule's cooldown ([com.tjcelaya.autopomodoro.data.PomodoroSchedule.cooldownMinutes])
 * has lapsed, per the one-shot alarm set in [AlarmSchedulerService.scheduleStatusDismiss].
 *
 * Its only job is to clear the persistent status notification — there is nothing useful left
 * to say until the next real alarm fires and [AlarmReceiver] posts a fresh status. This needs
 * no database access and no coroutine: [NotificationManager.cancel] is a fast, synchronous,
 * local call, so a plain [onReceive] is enough.
 */
class StatusDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = AlarmSchedulerService.extractScheduleId(intent)
        if (scheduleId == -1) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NotificationContent.statusNotificationId(scheduleId))
    }
}
