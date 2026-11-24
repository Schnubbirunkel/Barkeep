@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schnubbirunkel.barkeep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.Discovery
import de.schnubbirunkel.barkeep.data.Matcher
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.DrinkArt
import de.schnubbirunkel.barkeep.ui.Pill
import de.schnubbirunkel.barkeep.ui.RecipeTile
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.SectionTitle
import de.schnubbirunkel.barkeep.ui.appLang
import de.schnubbirunkel.barkeep.ui.parseColor
import de.schnubbirunkel.barkeep.ui.styleLabel
import kotlinx.coroutines.launch

val BROWSE_STYLES = listOf("sour", "tiki", "highball", "spritz", "stirred", "classic", "modern", "frozen", "sparkling", "bitter", "creamy", "coffee", "hot", "holiday", "smoky", "dessert")

@Composable
fun DiscoverScreen(vm: AppViewModel, nav: NavController) {
    val lang = appLang()
    val user by vm.user.collectAsState()
    val all by vm.allRecipes.collectAsState()
    val scope = rememberCoroutineScope()
    var dailyOffset by remember { mutableIntStateOf(0) }
    val inv = user.available
    val curated = remember(all) { all.filter { it.source == "local" || it.source == "custom" } }
    val forYou by vm.forYou.collectAsState()
    val becauseOf by vm.becauseOf.collectAsState()
    val daily = remember(forYou, curated, dailyOffset) { Discovery.daily(forYou.take(10).ifEmpty { curated }, offset = dailyOffset) }
    val makeable = remember(daily, inv) { daily != null && inv.isNotEmpty() && Matcher.match(daily, inv, vm.catalog).missingCount == 0 }
    val popular = remember(all) { Discovery.POPULAR.mapNotNull { id -> all.firstOrNull { it.id == id } } }
    val season = remember { Discovery.currentSeason() }
    val seasonal = remember(all) { curated.filter { r -> r.season.any { it in season } }.shuffled(java.util.Random(java.time.LocalDate.now().toEpochDay())) }

    run {
        // Header scrolls away with the content – nothing stays on top of the cards.
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + de.schnubbirunkel.barkeep.ui.LocalBottomBarSpace.current)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = de.schnubbirunkel.barkeep.ui.brandTitleStyle(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.greeting), style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.total_recipes, all.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(border = de.schnubbirunkel.barkeep.ui.glassBorder(),
                    onClick = { nav.navigate(Routes.FINDER) },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                ) {
                    Row(
                        Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f))))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.finder_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.finder_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.for_you))
                if (forYou.isEmpty()) Text(stringResource(R.string.for_you_empty), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(forYou, key = { it.id }) { r -> RecipeTile(r, lang, { nav.navigate(Routes.recipe(r.id)) }, section = "foryou") }
                }
            }
            if (daily != null) item {
                SectionTitle(stringResource(R.string.drink_of_day), action = {
                    IconButton(onClick = { dailyOffset++ }) { Icon(Icons.Filled.Shuffle, stringResource(R.string.random)) }
                })
                val c = parseColor(daily.color)
                Card(border = de.schnubbirunkel.barkeep.ui.glassBorder(),
                    onClick = { nav.navigate(Routes.recipe(daily.id)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = de.schnubbirunkel.barkeep.ui.glassFill()),
                ) {
                    Row(
                        Modifier.background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.22f), c.copy(alpha = 0.02f)))).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DrinkArt(daily, Modifier.size(110.dp), corner = 20.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(daily.displayName(lang), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(daily.about?.get(lang) ?: daily.ingredients.joinToString(", ") { vm.catalog.name(it, lang) },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (makeable) {
                                Spacer(Modifier.height(8.dp))
                                Pill(stringResource(R.string.daily_makeable), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
            becauseOf.forEach { (fav, list) ->
                item(key = "because" + fav.id) {
                    SectionTitle(stringResource(R.string.because_you_like, fav.displayName(lang)))
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(list, key = { it.id }) { r -> RecipeTile(r, lang, { nav.navigate(Routes.recipe(r.id)) }, section = "because" + fav.id) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(Icons.Filled.CameraAlt, stringResource(R.string.scan_cocktail), stringResource(R.string.scan_cocktail_sub), Modifier.weight(1f)) {
                        nav.navigate(Routes.scan("cocktail"))
                    }
                    ActionCard(Icons.Filled.DocumentScanner, stringResource(R.string.scan_ingredients), stringResource(R.string.scan_ingredients_sub), Modifier.weight(1f)) {
                        nav.navigate(Routes.scan("ingredients"))
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { all.randomOrNull()?.let { nav.navigate(Routes.recipe(it.id)) } }, label = { Text(stringResource(R.string.random)) },
                        leadingIcon = { Icon(Icons.Filled.Shuffle, null) })
                    if (user.settings.useOnline) AssistChip(
                        onClick = { scope.launch { vm.randomOnline()?.let { nav.navigate(Routes.recipe(it.id)) } } },
                        label = { Text("TheCocktailDB") }, leadingIcon = { Icon(Icons.Filled.Public, null) },
                    )
                }
            }
            if (seasonal.isNotEmpty()) item {
                SectionTitle(stringResource(R.string.seasonal))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(seasonal.take(15), key = { it.id }) { r -> RecipeTile(r, lang, { nav.navigate(Routes.recipe(r.id)) }, section = "season") }
                }
            }
            item {
                SectionTitle(stringResource(R.string.popular))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(popular, key = { it.id }) { r -> RecipeTile(r, lang, { nav.navigate(Routes.recipe(r.id)) }, section = "popular") }
                }
            }
            item {
                SectionTitle(stringResource(R.string.browse_styles))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mocktail", "punch", "smoothie", "shot").forEach { t ->
                        StyleChip(de.schnubbirunkel.barkeep.ui.typeLabel(t)) { nav.navigate(Routes.search(type = t)) }
                    }
                    BROWSE_STYLES.forEach { s -> StyleChip(styleLabel(s)) { nav.navigate(Routes.search(style = s)) } }
                }
            }
        }
    }
}

@Composable
private fun StyleChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(de.schnubbirunkel.barkeep.ui.glassFill(true))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun ActionCard(icon: ImageVector, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(border = de.schnubbirunkel.barkeep.ui.glassBorder(),onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = de.schnubbirunkel.barkeep.ui.glassFill())) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        }
    }
}
