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
import com.gymlog.app.ui.exercises.ExerciseListScreen
import com.gymlog.app.ui.templates.EditTemplateScreen
import com.gymlog.app.ui.templates.TemplateListScreen
import com.gymlog.app.ui.progress.ExerciseProgressScreen
import com.gymlog.app.ui.workout.ActiveWorkoutScreen

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun GymLogNavigation() {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Calendar, "Calendar", Icons.Default.DateRange),
        BottomNavItem(Screen.Templates, "Templates", Icons.AutoMirrored.Filled.List),
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
                        navController.navigate(Screen.TemplatePicker.route)
                    },
                    onResumeWorkout = { sessionId ->
                        // TODO: navigate to active workout with session id
                    }
                )
            }
            composable(Screen.Templates.route) {
                TemplateListScreen(
                    onTemplateClick = { templateId ->
                        navController.navigate(Screen.EditTemplate.createRoute(templateId))
                    },
                    onCreateClick = {
                        navController.navigate(Screen.CreateTemplate.route)
                    }
                )
            }
            composable(Screen.CreateTemplate.route) {
                EditTemplateScreen(
                    templateId = null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.EditTemplate.route) { backStackEntry ->
                val templateId = backStackEntry.arguments?.getString("templateId")?.toLongOrNull()
                EditTemplateScreen(
                    templateId = templateId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.TemplatePicker.route) {
                // TODO: implement TemplatePicker screen (Task 10)
                Text("Template Picker - Coming Soon")
            }
            composable(
                route = Screen.NewWorkout.route,
                arguments = listOf(navArgument("templateId") { type = NavType.StringType })
            ) { backStackEntry ->
                val templateId = backStackEntry.arguments?.getString("templateId")?.toLongOrNull()
                    ?: return@composable
                ActiveWorkoutScreen(
                    templateId = templateId,
                    onFinish = {
                        navController.popBackStack(Screen.Calendar.route, inclusive = false)
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
