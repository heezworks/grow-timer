package app.sn62kr.grow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import app.sn62kr.grow.R
import app.sn62kr.grow.ui.records.YearScreen
import app.sn62kr.grow.ui.records.YearViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.sn62kr.grow.Graph
import app.sn62kr.grow.GrowApp
import app.sn62kr.grow.ui.activities.ActivitiesScreen
import app.sn62kr.grow.ui.activities.ActivitiesViewModel
import app.sn62kr.grow.ui.home.HomeRoute
import app.sn62kr.grow.ui.home.HomeViewModel
import app.sn62kr.grow.ui.onboarding.OnboardingScreen
import app.sn62kr.grow.ui.onboarding.OnboardingViewModel
import app.sn62kr.grow.ui.theme.GrowTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph get() = (application as GrowApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GrowTheme { Surface(color = MaterialTheme.colorScheme.background) { AppNav(graph) } } }
    }

    override fun onResume() {
        super.onResume()
        // BOOT_COMPLETED는 늦게 온다(S3). 앱을 열 때도 기준을 다시 잡는다.
        lifecycleScope.launch { graph.rebaseTimer() }
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val YEAR = "year"
    const val ACTIVITIES = "activities"
}

/** 아래 탭: 홈 · 한 해. 친구 탭은 M4b에서(design.md 3절). */
private val tabs = listOf(Triple(Routes.HOME, R.string.tab_home, Icons.Default.Home), Triple(Routes.YEAR, R.string.tab_year, Icons.Default.DateRange))

@Composable
private fun AppNav(g: Graph) {
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        start = if (g.settings.isOnboarded.first() || g.records.hasAnyActivity()) Routes.HOME else Routes.ONBOARDING
    }
    val first = start ?: return Box(Modifier.fillMaxSize())
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (route == Routes.HOME || route == Routes.YEAR) NavigationBar {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { nav.navigate(r) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icon, null) }, label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    ) { pad ->
    NavHost(nav, startDestination = first, modifier = Modifier.padding(pad).consumeWindowInsets(pad)) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(viewModel { OnboardingViewModel(g) }) {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            }
        }
        composable(Routes.HOME) {
            HomeRoute(viewModel { HomeViewModel(g) }, onActivities = { nav.navigate(Routes.ACTIVITIES) })
        }
        composable(Routes.YEAR) {
            YearScreen(viewModel { YearViewModel(g) }, Modifier.statusBarsPadding())
        }
        composable(Routes.ACTIVITIES) {
            ActivitiesScreen(viewModel { ActivitiesViewModel(g) }, onBack = { nav.popBackStack() })
        }
    }
    }
}
