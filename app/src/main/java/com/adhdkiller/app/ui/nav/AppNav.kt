package com.adhdkiller.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.clickableNoRipple
import com.adhdkiller.app.ui.screens.blocker.BlockerScreen
import com.adhdkiller.app.ui.screens.dashboard.DashboardScreen
import com.adhdkiller.app.ui.screens.goals.GoalsScreen
import com.adhdkiller.app.ui.screens.habits.HabitsScreen
import com.adhdkiller.app.ui.screens.lockdown.LockdownScreen
import com.adhdkiller.app.ui.screens.schedule.ScheduleScreen
import com.adhdkiller.app.ui.screens.settings.SettingsScreen
import com.adhdkiller.app.ui.screens.stats.StatsScreen
import com.adhdkiller.app.ui.screens.tasks.TasksScreen
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary

object Routes {
    const val TODAY = "today"
    const val TASKS = "tasks"
    const val HABITS = "habits"
    const val GOALS = "goals"
    const val SCHEDULE = "schedule"
    const val LOCKDOWN = "lockdown"
    const val BLOCKER = "blocker"
    const val SETTINGS = "settings"
    const val STATS = "stats"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.TODAY, "Today", Icons.Default.Home),
    TabItem(Routes.TASKS, "Tasks", Icons.Default.Check),
    TabItem(Routes.HABITS, "Habits", Icons.Default.Star),
    TabItem(Routes.GOALS, "Goals", Icons.Default.Favorite),
    TabItem(Routes.SCHEDULE, "Schedule", Icons.Default.DateRange),
)

@Composable
fun AppNav() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
            ) {
                composable(Routes.TODAY) {
                    DashboardScreen(
                        onNavigate = { route -> navController.navigate(route) },
                    )
                }
                composable(Routes.TASKS) { TasksScreen() }
                composable(Routes.HABITS) { HabitsScreen() }
                composable(Routes.GOALS) { GoalsScreen() }
                composable(Routes.SCHEDULE) { ScheduleScreen() }
                composable(Routes.LOCKDOWN) {
                    LockdownScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.BLOCKER) {
                    BlockerScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.STATS) {
                    StatsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        if (showBottomBar) {
            BottomBar(currentRoute = currentRoute, onSelect = { route -> navController.switchTab(route) })
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == currentRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickableNoRipple { onSelect(tab.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) Color.White else TextTertiary,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else TextTertiary,
                    )
                }
            }
        }
    }
}
