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
import com.tjcelaya.autopomodoro.R
import com.tjcelaya.autopomodoro.AutopomodoroApp
import com.tjcelaya.autopomodoro.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = AlarmSchedulerService.extractScheduleId(intent)
        if (scheduleId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).scheduleDao()
                val schedule = dao.getById(scheduleId) ?: return@launch

                // Post notification
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
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

                    val notification = NotificationCompat.Builder(
                        context,
                        AutopomodoroApp.CHANNEL_ID,
                    )
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("autopomodoro")
                        .setContentText(schedule.name)
                        .setContentIntent(contentPendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()

                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm.notify(scheduleId, notification)
                }

                // Schedule the next alarm for this schedule
                AlarmSchedulerService.scheduleNext(context, schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
