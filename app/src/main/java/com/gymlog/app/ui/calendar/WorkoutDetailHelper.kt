package com.gymlog.app.ui.calendar

import com.gymlog.app.data.ExerciseSet

fun groupSetsByExercise(sets: List<ExerciseSet>): List<Pair<Long, List<ExerciseSet>>> {
    val grouped = linkedMapOf<Long, MutableList<ExerciseSet>>()
    for (set in sets) {
        grouped.getOrPut(set.exerciseId) { mutableListOf() }.add(set)
    }
    return grouped.map { (exerciseId, exerciseSets) -> exerciseId to exerciseSets.toList() }
}
