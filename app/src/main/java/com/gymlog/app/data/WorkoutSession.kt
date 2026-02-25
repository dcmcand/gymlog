package com.gymlog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("workoutId"), Index("date")]
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long?,
    val date: LocalDate,
    val status: SessionStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null
)
