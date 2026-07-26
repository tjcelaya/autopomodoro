package com.tjcelaya.autopomodoro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjcelaya.autopomodoro.data.AppDatabase
import com.tjcelaya.autopomodoro.data.PomodoroSchedule
import com.tjcelaya.autopomodoro.scheduler.AlarmSchedulerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).scheduleDao()

    val schedules: StateFlow<List<PomodoroSchedule>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(schedule: PomodoroSchedule) {
        viewModelScope.launch {
            val id = dao.upsert(schedule).toInt()
            val saved = schedule.copy(id = if (schedule.id == 0) id else schedule.id)
            AlarmSchedulerService.scheduleNext(getApplication(), saved)
        }
    }

    fun delete(schedule: PomodoroSchedule) {
        viewModelScope.launch {
            AlarmSchedulerService.cancel(getApplication(), schedule.id)
            dao.delete(schedule)
        }
    }

    fun toggleEnabled(schedule: PomodoroSchedule) {
        viewModelScope.launch {
            val newEnabled = !schedule.isEnabled
            dao.setEnabled(schedule.id, newEnabled)
            val updated = schedule.copy(isEnabled = newEnabled)
            AlarmSchedulerService.scheduleNext(getApplication(), updated)
        }
    }

    suspend fun getById(id: Int): PomodoroSchedule? = dao.getById(id)
}
