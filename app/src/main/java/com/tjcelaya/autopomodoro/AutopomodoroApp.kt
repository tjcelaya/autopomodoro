package com.tjcelaya.autopomodoro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tjcelaya.autopomodoro.data.AppDatabase
import com.tjcelaya.autopomodoro.scheduler.AlarmSchedulerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutopomodoroApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Re-register alarms on every app launch as a safety net
        CoroutineScope(Dispatchers.IO).launch {
            AlarmSchedulerService.rescheduleAll(this@AutopomodoroApp)
        }
    }

    private fun createNotificationChannels() {
        val alarmChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_alarms_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_alarms_description)
        }
        // Separate, low-importance channel for the ongoing status notification: it updates
        // silently as the cycle progresses and must never buzz the way an actual alarm does.
        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_status_description)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(alarmChannel, statusChannel))
    }

    companion object {
        const val CHANNEL_ID = "autopomodoro_alarms"
        const val STATUS_CHANNEL_ID = "autopomodoro_status"
    }
}
