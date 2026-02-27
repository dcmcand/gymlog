package com.gymlog.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymlog.app.ui.calendar.CalendarScreen
import com.gymlog.app.ui.calendar.WorkoutDetailScreen
import com.gymlog.app.ui.exercises.ExerciseListScreen
import com.gymlog.app.ui.workouts.EditWorkoutScreen
import com.gymlog.app.ui.workouts.WorkoutListScreen
import com.gymlog.app.ui.progress.ExerciseProgressScreen
import com.gymlog.app.ui.workout.ActiveWorkoutScreen
import com.gymlog.app.ui.workout.WorkoutPickerScreen

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun GymLogNavigation(
    pendingSessionId: Long? = null,
    onPendingSessionConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(pendingSessionId) {
        if (pendingSessionId != null) {
            navController.navigate(Screen.ResumeWorkout.createRoute(pendingSessionId)) {
                launchSingleTop = true
            }
            onPendingSessionConsumed()
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Calendar, "Calendar", Icons.Default.DateRange),
        BottomNavItem(Screen.Workouts, "Workouts", Icons.AutoMirrored.Filled.List),
        BottomNavItem(Screen.Exercises, "Exercises", Icons.Default.FitnessCenter)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calendar.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onNewWorkoutClick = {
                        navController.navigate(Screen.WorkoutPicker.route)
                    },
                    onResumeWorkout = { sessionId ->
                        navController.navigate(Screen.ResumeWorkout.createRoute(sessionId))
                    },
                    onWorkoutClick = { sessionId ->
                        navController.navigate(Screen.WorkoutDetail.createRoute(sessionId))
                    }
                )
            }
            composable(Screen.Workouts.route) {
                WorkoutListScreen(
                    onWorkoutClick = { workoutId ->
                        navController.navigate(Screen.EditWorkout.createRoute(workoutId))
                    },
                    onCreateClick = {
                        navController.navigate(Screen.CreateWorkout.route)
                    }
                )
            }
            composable(Screen.CreateWorkout.route) {
                EditWorkoutScreen(
                    workoutId = null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.EditWorkout.route) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getString("workoutId")?.toLongOrNull()
                EditWorkoutScreen(
                    workoutId = workoutId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.WorkoutPicker.route) {
                WorkoutPickerScreen(
                    onWorkoutPicked = { workoutId ->
                        navController.navigate(Screen.NewWorkout.createRoute(workoutId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.NewWorkout.route,
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getString("workoutId")?.toLongOrNull()
                    ?: return@composable
                ActiveWorkoutScreen(
                    workoutId = workoutId,
                    onFinish = {
                        navController.popBackStack(Screen.Calendar.route, inclusive = false)
                    }
                )
            }
            composable(
                route = Screen.ResumeWorkout.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull()
                    ?: return@composable
                ActiveWorkoutScreen(
                    resumeSessionId = sessionId,
                    onFinish = {
                        navController.popBackStack(Screen.Calendar.route, inclusive = false)
                    }
                )
            }
            composable(
                route = Screen.WorkoutDetail.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull()
                    ?: return@composable
                WorkoutDetailScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Exercises.route) {
                ExerciseListScreen(
                    onExerciseClick = { exerciseId ->
                        navController.navigate(Screen.ExerciseProgress.createRoute(exerciseId))
                    }
                )
            }
            composable(
                route = Screen.ExerciseProgress.route,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val exerciseId = backStackEntry.arguments?.getString("exerciseId")?.toLongOrNull()
                    ?: return@composable
                ExerciseProgressScreen(
                    exerciseId = exerciseId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
