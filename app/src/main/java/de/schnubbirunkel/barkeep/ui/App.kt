@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package de.schnubbirunkel.barkeep.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
    val selected = tabs.indexOfFirst { it.route == current }
    val showBar = selected >= 0 || current == null
    val barSpace = if (showBar) 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    val open = user.shopping.count { !it.done }
    val fast = tween<Float>(200)

    // Screens sit directly on the liquid background (no opaque Surface), so set the text colour explicitly.
    CompositionLocalProvider(
        LocalSnackbar provides snackbar, LocalBottomBarSpace provides barSpace,
        androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    ) {
        ProvideHero {
            Box(Modifier.fillMaxSize()) {
                LiquidBackground()
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedScope provides this) {
                        NavHost(
                            nav, startDestination = start, modifier = Modifier.fillMaxSize(),
                            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
                            exitTransition = { fadeOut(tween(160)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { fadeOut(tween(160)) + scaleOut(fast, targetScale = 0.97f) },
                        ) {
                            screen(Routes.DISCOVER) { DiscoverScreen(vm, nav) }
                            screen(
                                Routes.SEARCH,
                                listOf(
                                    navArgument("style") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("type") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("q") { type = NavType.StringType; defaultValue = "" },
                                ),
                            ) { e ->
                                SearchScreen(vm, nav, e.arguments?.getString("style").orEmpty(), e.arguments?.getString("type").orEmpty(), e.arguments?.getString("q").orEmpty())
                            }
                            screen(Routes.BAR, listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { e ->
                                BarScreen(vm, nav, e.arguments?.getInt("tab") ?: 0)
                            }
                            screen(Routes.FAVORITES) { FavoritesScreen(vm, nav) }
                            screen(Routes.SHOPPING) { ShoppingScreen(vm, nav) }
                            screen(Routes.RECIPE, listOf(navArgument("id") { type = NavType.StringType })) { e ->
                                DetailScreen(vm, nav, e.arguments?.getString("id").orEmpty())
                            }
                            screen(
                                Routes.EDIT,
                                listOf(
                                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("base") { type = NavType.StringType; defaultValue = "" },
                                ),
                            ) { e -> EditorScreen(vm, nav, e.arguments?.getString("id").orEmpty(), e.arguments?.getString("base").orEmpty()) }
                            screen(Routes.SCAN, listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "cocktail" })) { e ->
                                ScanScreen(vm, nav, e.arguments?.getString("mode") ?: "cocktail")
                            }
                            screen(Routes.SETTINGS) { SettingsScreen(vm, nav) }
                            screen(Routes.FINDER) { de.schnubbirunkel.barkeep.ui.screens.FinderScreen(vm, nav) }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showBar, modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
                ) {
                    GlassNavBar(
                        items = tabs.map { NavItem(stringResource(it.label), it.icon, if (it.route == Routes.SHOPPING) open else 0) },
                        selected = selected,
                        onSelect = { i ->
                            nav.navigate(tabs[i].nav) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = barSpace))
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

/** composable() that also exposes its AnimatedVisibilityScope for shared element transitions. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { e ->
    CompositionLocalProvider(LocalNavAnimScope provides this) { content(e) }
}

@Composable
fun SnackOnce(message: String?, onShown: () -> Unit) {
    val sb = LocalSnackbar.current
    LaunchedEffect(message) {
        if (message != null) { sb.showSnackbar(message); onShown() }
    }
}
