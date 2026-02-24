package com.gymlog.app.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.gymlog.app.data.Exercise
import com.gymlog.app.data.ExerciseSet

class ActiveWorkoutState {
    private val exerciseList = mutableStateListOf<Exercise>()
    private val setsByExercise = mutableMapOf<Long, MutableList<ExerciseSet>>()

    var version by mutableIntStateOf(0)
        private set

    val exercises: List<Exercise> get() = exerciseList

    fun addExercise(exercise: Exercise, sets: List<ExerciseSet>) {
        exerciseList.add(exercise)
        setsByExercise[exercise.id] = sets.toMutableList()
        version++
    }

    fun getExerciseSets(exerciseId: Long): List<ExerciseSet> {
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
