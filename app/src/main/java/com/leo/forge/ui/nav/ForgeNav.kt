package com.leo.forge.ui.nav

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.leo.forge.data.prefs.ForgeSettings
import com.leo.forge.domain.model.Units
import com.leo.forge.ui.gym.GymScreen
import com.leo.forge.ui.history.SessionDetailScreen
import com.leo.forge.ui.program.PlanScreen
import com.leo.forge.ui.progress.ExerciseDetailScreen
import com.leo.forge.ui.progress.ProgressScreen
import com.leo.forge.ui.session.SessionScreen
import com.leo.forge.ui.settings.SettingsScreen
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.LocalHapticsEnabled
import com.leo.forge.ui.theme.LocalUnits
import com.leo.forge.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val PROGRESS = "progress"
    const val PLAN = "plan"
    const val SETTINGS = "settings"

    const val GYM = "gym"
    const val SESSION = "session"
    const val SESSION_DETAIL = "session_detail/{id}"
    const val EXERCISE_DETAIL = "exercise_detail/{id}"
    fun sessionDetail(id: Long) = "session_detail/$id"
    fun exerciseDetail(id: String) = "exercise_detail/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/**
 * Four tabs, not six.
 *
 * History, records and charts are all "what already happened", so they are one destination
 * with a segmented control rather than three places to look. The gym belongs to planning and
 * sits one tap inside it.
 */
private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Rounded.Today),
    Tab(Routes.PROGRESS, "Progress", Icons.Rounded.TrendingUp),
    Tab(Routes.PLAN, "Plan", Icons.Rounded.CalendarMonth),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun ForgeRoot(settings: ForgeSettings, units: Units) = CompositionLocalProvider(
    LocalHapticsEnabled provides settings.haptics,
    LocalUnits provides units,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    // The active workout is full-bleed: nothing competes with the next set.
    val showBar = tabs.any { t -> current?.hierarchy?.any { it.route == t.route } == true }

    Scaffold(
        containerColor = Forge.colors.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = Forge.colors.surface1, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val selected = current?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label, Modifier.size(22.dp)) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Forge.colors.onAccent,
                                selectedTextColor = Forge.colors.accent,
                                indicatorColor = Forge.colors.accent,
                                unselectedIconColor = Forge.colors.textTertiary,
                                unselectedTextColor = Forge.colors.textTertiary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding).background(Forge.colors.background),
            enterTransition = { fadeIn(tween(160)) + slideInHorizontally(tween(260)) { it / 14 } },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(260)) { it / 14 } },
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onStartWorkout = { nav.navigate(Routes.SESSION) },
                    onOpenProgram = { nav.navigate(Routes.PLAN) },
                )
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(
                    onOpenSession = { nav.navigate(Routes.sessionDetail(it)) },
                    onOpenExercise = { nav.navigate(Routes.exerciseDetail(it)) },
                )
            }
            composable(Routes.PLAN) {
                PlanScreen(onOpenGym = { nav.navigate(Routes.GYM) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }

            composable(Routes.GYM) { GymScreen() }
            composable(Routes.SESSION) {
                SessionScreen(settings = settings, onDone = { nav.popBackStack() })
            }
            composable(Routes.SESSION_DETAIL) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                SessionDetailScreen(sessionId = id, onBack = { nav.popBackStack() })
            }
            composable(Routes.EXERCISE_DETAIL) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ExerciseDetailScreen(exerciseId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
