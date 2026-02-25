package com.gymlog.app.ui.navigation

sealed class Screen(val route: String) {
    data object Calendar : Screen("calendar")
    data object Templates : Screen("templates")
    data object Exercises : Screen("exercises")
    data object TemplatePicker : Screen("template_picker")
    data object NewWorkout : Screen("new_workout/{templateId}") {
        fun createRoute(templateId: Long) = "new_workout/$templateId"
    }
    data object ExerciseProgress : Screen("exercise_progress/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_progress/$exerciseId"
    }
    data object EditTemplate : Screen("edit_template/{templateId}") {
        fun createRoute(templateId: Long) = "edit_template/$templateId"
    }
    data object CreateTemplate : Screen("create_template")
    data object WorkoutDetail : Screen("workout_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "workout_detail/$sessionId"
    }
}
