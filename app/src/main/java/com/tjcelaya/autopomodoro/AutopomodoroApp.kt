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
        createNotificationChannel()
        // Re-register alarms on every app launch as a safety net
        CoroutineScope(Dispatchers.IO).launch {
            AlarmSchedulerService.rescheduleAll(this@AutopomodoroApp)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "autopomodoro",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Cycle-based alarm notifications"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "autopomodoro_alarms"
    }
}
