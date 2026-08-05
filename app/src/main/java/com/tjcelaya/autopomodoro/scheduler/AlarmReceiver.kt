package com.tjcelaya.autopomodoro.scheduler

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tjcelaya.autopomodoro.MainActivity
import com.tjcelaya.autopomodoro.AutopomodoroApp
import com.tjcelaya.autopomodoro.data.AppDatabase
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Thin by design: every decision about wording and iconography is made by the pure
 * [NotificationContent] object, which is unit-tested under `app/src/test`. This class only
 * resolves the resource ids/args it returns against a real [Context] and calls into
 * [NotificationManager] / [AlarmSchedulerService].
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = AlarmSchedulerService.extractScheduleId(intent)
        if (scheduleId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).scheduleDao()
                val schedule = dao.getById(scheduleId) ?: return@launch
                val now = LocalDateTime.now()

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    postAlarmNotification(context, scheduleId, schedule, now)
                }

                // Intentionally outside the permission check: StatusNotifier applies its own
                // before posting, and its clear path still has to run when posting is denied.
                StatusNotifier.update(context, schedule, now)

                // Schedule the next alarm for this schedule
                AlarmSchedulerService.scheduleNext(context, schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postAlarmNotification(
        context: Context,
        scheduleId: Int,
        schedule: PomodoroSchedule,
        firedAt: LocalDateTime,
    ) {
        val content = NotificationContent.forAlarmFired(schedule, firedAt)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SCHEDULE_ID, scheduleId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            scheduleId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, AutopomodoroApp.CHANNEL_ID)
            .setSmallIcon(content.iconRes)
            .setContentTitle(schedule.name)
            .setContentText(context.getString(content.textRes, *content.textArgs.toTypedArray()))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(scheduleId, notification)
    }

}
