package com.tjcelaya.autopomodoro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY name ASC")
    fun getAll(): Flow<List<PomodoroSchedule>>

    @Query("SELECT * FROM schedules WHERE isEnabled = 1")
    suspend fun getEnabled(): List<PomodoroSchedule>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Int): PomodoroSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: PomodoroSchedule): Long

    @Delete
    suspend fun delete(schedule: PomodoroSchedule)

    @Query("UPDATE schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}
