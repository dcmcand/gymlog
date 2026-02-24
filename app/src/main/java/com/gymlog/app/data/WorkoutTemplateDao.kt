package com.gymlog.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY name ASC")
    fun getAll(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplate?

    @Insert
    suspend fun insert(template: WorkoutTemplate): Long

    @Update
    suspend fun update(template: WorkoutTemplate)

    @Delete
    suspend fun delete(template: WorkoutTemplate)

    @Query("SELECT * FROM workout_template_exercises WHERE templateId = :templateId ORDER BY sortOrder ASC")
    suspend fun getExercisesForTemplate(templateId: Long): List<WorkoutTemplateExercise>

    @Insert
    suspend fun insertTemplateExercise(templateExercise: WorkoutTemplateExercise): Long

    @Query("DELETE FROM workout_template_exercises WHERE templateId = :templateId")
    suspend fun deleteAllExercisesForTemplate(templateId: Long)
}
