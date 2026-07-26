package com.tjcelaya.autopomodoro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "schedules")
data class PomodoroSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val cycleStartDate: LocalDate,
    val daysOn: Int,
    val daysOff: Int,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val intervalMinutes: Int,
    val isEnabled: Boolean = true,
)
