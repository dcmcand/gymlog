package com.gymlog.app.ui.workout

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet

class ActiveWorkoutState {
    private val exerciseList = mutableStateListOf<Exercise>()
    private val setsByExercise = mutableMapOf<Long, SnapshotStateList<ExerciseSet>>()

    var version: Int = 0
        private set

    val exercises: List<Exercise> get() = exerciseList

    fun addExercise(exercise: Exercise, sets: List<ExerciseSet>) {
        exerciseList.add(exercise)
        val stateList = mutableStateListOf<ExerciseSet>()
        stateList.addAll(sets)
        setsByExercise[exercise.id] = stateList
        version++
    }

    fun getExerciseSets(exerciseId: Long): SnapshotStateList<ExerciseSet>? {
        return setsByExercise[exerciseId]
    }

    fun getExerciseSetsCopy(exerciseId: Long): List<ExerciseSet> {
        return setsByExercise[exerciseId]?.toList() ?: emptyList()
    }

    fun updateSet(exerciseId: Long, index: Int, updatedSet: ExerciseSet) {
        setsByExercise[exerciseId]?.let { sets ->
            sets[index] = updatedSet
            version++
        }
    }

    fun addSet(exerciseId: Long, set: ExerciseSet) {
        setsByExercise[exerciseId]?.let { sets ->
            sets.add(set)
            version++
        }
    }
}
