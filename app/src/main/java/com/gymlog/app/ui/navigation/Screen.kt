package com.gymlog.app.ui.navigation

sealed class Screen(val route: String) {
    data object Calendar : Screen("calendar")
    data object Workouts : Screen("workouts")
    data object Exercises : Screen("exercises")
    data object WorkoutPicker : Screen("workout_picker")
    data object NewWorkout : Screen("new_workout/{workoutId}") {
        fun createRoute(workoutId: Long) = "new_workout/$workoutId"
    }
    data object ExerciseProgress : Screen("exercise_progress/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_progress/$exerciseId"
    }
    data object EditWorkout : Screen("edit_workout/{workoutId}") {
        fun createRoute(workoutId: Long) = "edit_workout/$workoutId"
    }
    data object CreateWorkout : Screen("create_workout")
    data object WorkoutDetail : Screen("workout_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "workout_detail/$sessionId"
    }
    data object ResumeWorkout : Screen("resume_workout/{sessionId}") {
        fun createRoute(sessionId: Long) = "resume_workout/$sessionId"
    }
}
