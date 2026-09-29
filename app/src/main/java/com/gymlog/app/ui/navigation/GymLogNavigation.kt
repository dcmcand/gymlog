package com.gymlog.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.gymlog.app.data.backup.DataReplaced
import com.gymlog.app.ui.calendar.CalendarScreen
import com.gymlog.app.ui.calendar.WorkoutDetailScreen
import com.gymlog.app.ui.exercises.ExerciseListScreen
import com.gymlog.app.ui.progress.ExerciseProgressScreen
import com.gymlog.app.ui.settings.SettingsScreen
import com.gymlog.app.ui.workout.ActiveWorkoutScreen
import com.gymlog.app.ui.workout.WorkoutPickerScreen
import com.gymlog.app.ui.workouts.EditWorkoutScreen
import com.gymlog.app.ui.workouts.WorkoutListScreen

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

    // After an import, saved tab back stacks (e.g. an open Edit Workout) may refer to rows that
    // no longer exist; drop them so those tabs start fresh. The handled count is saveable so a
    // rotation doesn't clear them again.
    val importsDone by DataReplaced.generation.collectAsState()
    var importsHandled by rememberSaveable { mutableIntStateOf(importsDone) }
    LaunchedEffect(importsDone) {
        if (importsDone > importsHandled) {
            navController.clearBackStack(Screen.Workouts.route)
            navController.clearBackStack(Screen.Exercises.route)
        }
        // Always catch up: after process death the count restarts at 0 but the saved value
        // doesn't, and staying ahead would make the next import skip the reset.
        importsHandled = importsDone
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
        // Each screen's own Scaffold/TopAppBar handles the status bar (the app is edge-to-edge
        // since it targets SDK 35+); applying it here too pushed every title bar down by a
        // second status-bar height.
        contentWindowInsets = WindowInsets(0),
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
            // Tell the screens the bottom bar already covers the navigation-bar inset.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
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
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
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
                    onNavigateBack = { navController.popBackStack() },
                    onReopen = { sid ->
                        navController.navigate(Screen.ResumeWorkout.createRoute(sid)) {
                            popUpTo(Screen.Calendar.route)
                        }
                    }
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
