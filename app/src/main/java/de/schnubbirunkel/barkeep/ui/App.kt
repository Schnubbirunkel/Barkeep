@file:OptIn(ExperimentalMaterial3Api::class)

package de.schnubbirunkel.barkeep.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.ui.screens.BarScreen
import de.schnubbirunkel.barkeep.ui.screens.DetailScreen
import de.schnubbirunkel.barkeep.ui.screens.DiscoverScreen
import de.schnubbirunkel.barkeep.ui.screens.EditorScreen
import de.schnubbirunkel.barkeep.ui.screens.FavoritesScreen
import de.schnubbirunkel.barkeep.ui.screens.ScanScreen
import de.schnubbirunkel.barkeep.ui.screens.SearchScreen
import de.schnubbirunkel.barkeep.ui.screens.SettingsScreen
import de.schnubbirunkel.barkeep.ui.screens.ShoppingScreen

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

object Routes {
    const val DISCOVER = "discover"
    const val SEARCH = "search?style={style}&type={type}&q={q}"
    const val BAR = "bar?tab={tab}"
    const val FAVORITES = "favorites"
    const val SHOPPING = "shopping"
    const val RECIPE = "recipe/{id}"
    const val EDIT = "edit?id={id}&base={base}"
    const val SCAN = "scan?mode={mode}"
    const val SETTINGS = "settings"
    const val FINDER = "finder"

    fun search(style: String = "", type: String = "", q: String = "") = "search?style=${Uri.encode(style)}&type=${Uri.encode(type)}&q=${Uri.encode(q)}"
    fun bar(tab: Int = 0) = "bar?tab=$tab"
    fun recipe(id: String) = "recipe/${Uri.encode(id)}"
    fun edit(id: String = "", base: String = "") = "edit?id=${Uri.encode(id)}&base=${Uri.encode(base)}"
    fun scan(mode: String) = "scan?mode=$mode"
}

private data class Tab(val route: String, val nav: String, val label: Int, val icon: ImageVector)

@Composable
fun BarkeepApp(vm: AppViewModel, start: String = Routes.DISCOVER, nav: NavHostController = rememberNavController()) {
    val snackbar = remember { SnackbarHostState() }
    val user by vm.user.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val tabs = listOf(
        Tab(Routes.DISCOVER, Routes.DISCOVER, R.string.nav_discover, Icons.Filled.LocalBar),
        Tab(Routes.SEARCH, Routes.search(), R.string.nav_search, Icons.Filled.Search),
        Tab(Routes.BAR, Routes.bar(), R.string.nav_bar, Icons.Filled.Liquor),
        Tab(Routes.FAVORITES, Routes.FAVORITES, R.string.nav_favorites, Icons.Filled.Favorite),
        Tab(Routes.SHOPPING, Routes.SHOPPING, R.string.nav_shopping, Icons.Filled.ShoppingCart),
    )
    val showBar = tabs.any { it.route == current } || current == null
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar) NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = {
                                nav.navigate(t.nav) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val open = user.shopping.count { !it.done }
                                if (t.route == Routes.SHOPPING && open > 0) BadgedBox(badge = { Badge { Text("$open") } }) { Icon(t.icon, null) }
                                else Icon(t.icon, null)
                            },
                            label = { Text(stringResource(t.label), maxLines = 1) },
                        )
                    }
                }
            },
        ) { pad ->
            NavHost(nav, startDestination = start, modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
                composable(Routes.DISCOVER) { DiscoverScreen(vm, nav) }
                composable(
                    Routes.SEARCH,
                    arguments = listOf(
                        navArgument("style") { type = NavType.StringType; defaultValue = "" },
                        navArgument("type") { type = NavType.StringType; defaultValue = "" },
                        navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { e ->
                    SearchScreen(vm, nav, e.arguments?.getString("style").orEmpty(), e.arguments?.getString("type").orEmpty(), e.arguments?.getString("q").orEmpty())
                }
                composable(Routes.BAR, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { e ->
                    BarScreen(vm, nav, e.arguments?.getInt("tab") ?: 0)
                }
                composable(Routes.FAVORITES) { FavoritesScreen(vm, nav) }
                composable(Routes.SHOPPING) { ShoppingScreen(vm, nav) }
                composable(Routes.RECIPE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    DetailScreen(vm, nav, e.arguments?.getString("id").orEmpty())
                }
                composable(
                    Routes.EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; defaultValue = "" },
                        navArgument("base") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { e -> EditorScreen(vm, nav, e.arguments?.getString("id").orEmpty(), e.arguments?.getString("base").orEmpty()) }
                composable(Routes.SCAN, arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "cocktail" })) { e ->
                    ScanScreen(vm, nav, e.arguments?.getString("mode") ?: "cocktail")
                }
                composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
                composable(Routes.FINDER) { de.schnubbirunkel.barkeep.ui.screens.FinderScreen(vm, nav) }
            }
        }
        if (!user.onboarded && start == Routes.DISCOVER) {
            AlertDialog(
                onDismissRequest = { vm.setOnboarded() },
                title = { Text(stringResource(R.string.onboarding_title)) },
                text = { Text(stringResource(R.string.onboarding_text)) },
                confirmButton = { TextButton(onClick = { vm.setOnboarded(); nav.navigate(Routes.bar(1)) }) { Text(stringResource(R.string.onboarding_go)) } },
                dismissButton = { TextButton(onClick = { vm.setOnboarded() }) { Text(stringResource(R.string.later)) } },
            )
        }
    }
}

@Composable
fun SnackOnce(message: String?, onShown: () -> Unit) {
    val sb = LocalSnackbar.current
    LaunchedEffect(message) {
        if (message != null) { sb.showSnackbar(message); onShown() }
    }
}
