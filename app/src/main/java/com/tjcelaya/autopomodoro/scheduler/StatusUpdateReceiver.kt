package com.tjcelaya.autopomodoro.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tjcelaya.autopomodoro.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Fires on the one-shot refresh alarm set by [AlarmSchedulerService.scheduleStatusRefresh] and
 * re-runs [StatusNotifier.update], which re-posts the status notification with a current
 * relative duration and arms the following refresh.
 *
 * Replaces the earlier dismiss-only receiver. That one could just call
 * [android.app.NotificationManager.cancel] synchronously; this needs the schedule row to
 * recompute the phase, so it goes through the database on [Dispatchers.IO] under [goAsync].
 *
 * A missing row means the schedule was deleted while a refresh was pending — clear rather than
 * leave the notification orphaned.
 */
class StatusUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = AlarmSchedulerService.extractScheduleId(intent)
        if (scheduleId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedule = AppDatabase.getInstance(context).scheduleDao().getById(scheduleId)
                if (schedule == null) {
                    StatusNotifier.clear(context, scheduleId)
                } else {
                    StatusNotifier.update(context, schedule, LocalDateTime.now())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
