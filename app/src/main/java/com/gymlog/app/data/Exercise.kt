package com.gymlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ExerciseType,
    val cardioFixedDimension: CardioFixedDimension? = null,
    val fixedValue: Int? = null,
    val level: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val distanceDisplayKm: Boolean = false
)

fun Exercise.displayName(): String {
    if (cardioFixedDimension == null) return name

    val valuePart = when (cardioFixedDimension) {
        CardioFixedDimension.DISTANCE -> {
            if (distanceDisplayKm) "${fixedValue!! / 1000}k" else "${fixedValue}m"
        }
        CardioFixedDimension.TIME -> "${fixedValue!! / 60}min"
    }

    val levelPart = if (level != null) " - L$level" else ""

    return "$name - $valuePart$levelPart"
}
