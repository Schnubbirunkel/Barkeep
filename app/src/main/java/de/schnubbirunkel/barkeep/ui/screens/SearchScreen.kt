@file:OptIn(ExperimentalMaterial3Api::class)

package de.schnubbirunkel.barkeep.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.Catalog
import de.schnubbirunkel.barkeep.data.Matcher
import de.schnubbirunkel.barkeep.data.Recipe
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.EmptyHint
import de.schnubbirunkel.barkeep.ui.Labels
import de.schnubbirunkel.barkeep.ui.Pill
import de.schnubbirunkel.barkeep.ui.RecipeRow
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.SectionTitle
import de.schnubbirunkel.barkeep.ui.appLang
import de.schnubbirunkel.barkeep.ui.styleLabel
import de.schnubbirunkel.barkeep.ui.typeLabel

val SPIRIT_GROUPS: Map<String, Set<String>> = mapOf(
    "gin" to setOf("gin", "sloe_gin"),
    "vodka" to setOf("vodka"),
    "rum" to setOf("white_rum", "dark_rum", "aged_rum", "overproof_rum", "cachaca"),
    "agave" to setOf("tequila", "reposado", "mezcal"),
    "whiskey" to setOf("bourbon", "rye", "scotch", "islay_scotch", "irish_whiskey"),
    "brandy" to setOf("cognac", "brandy", "calvados", "pisco"),
    "wine" to setOf("sparkling_wine", "champagne", "prosecco", "red_wine", "white_wine", "lillet", "sweet_vermouth", "dry_vermouth", "blanc_vermouth", "port", "dry_sherry"),
)

private val SEARCH_STYLES = listOf("sour", "highball", "tiki", "spritz", "stirred", "classic", "modern", "frozen", "sparkling", "hot", "bitter", "creamy", "coffee", "smoky", "holiday", "dessert", "low_abv")

/** Text search over names (en/de), ingredients (en/de) and styles. */
fun matchesQuery(r: Recipe, q: String, catalog: Catalog): Boolean {
    if (q.isBlank()) return true
    val words = Catalog.spelling(Catalog.norm(q)).split(' ').filter { it.isNotBlank() }
    val hay = haystack(r, catalog)
    return words.all { it in hay }
}

fun haystack(r: Recipe, catalog: Catalog): String = buildString {
        append(Catalog.norm(r.name)); append(' ')
        r.nameDe?.let { append(Catalog.norm(it)); append(' ') }
        r.ingredients.forEach { i ->
            val id = catalog.idOf(i)
            append(catalog.terms(id)); append(' ')
            i.name?.let { append(Catalog.norm(it)); append(' ') }
        }
        r.styles.forEach { append(it); append(' ') }
        append(r.type)
    }.let { Catalog.spelling(it) }

@Composable
fun SearchScreen(vm: AppViewModel, nav: NavController, initStyle: String, initType: String, initQuery: String) {
    val lang = appLang()
    val all by vm.allRecipes.collectAsState()
    val user by vm.user.collectAsState()
    val online by vm.onlineResults.collectAsState()
    val onlineError by vm.onlineError.collectAsState()
    var query by rememberSaveable { mutableStateOf(initQuery) }
    var type by rememberSaveable { mutableStateOf(initType) }
    var spirit by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf(initStyle) }
    var onlyMakeable by rememberSaveable { mutableStateOf(false) }
    val styleLabels = SEARCH_STYLES.associateWith { styleLabel(it) }

    val index = remember(all) { HashMap<String, String>(all.size * 2).apply { all.forEach { put(it.id, haystack(it, vm.catalog)) } } }
    val results by remember(all, user.available) {
        derivedStateOf {
            val words = Catalog.spelling(Catalog.norm(query)).split(' ').filter { it.isNotBlank() }
            all.asSequence()
                .filter { type.isEmpty() || it.type == type }
                .filter { style.isEmpty() || style in it.styles }
                .filter { spirit.isEmpty() || it.ingredients.any { i -> vm.catalog.idOf(i) in SPIRIT_GROUPS[spirit].orEmpty() } }
                .filter { !onlyMakeable || Matcher.match(it, user.available, vm.catalog).missingCount == 0 }
                .filter { r -> words.isEmpty() || index[r.id].orEmpty().let { h -> words.all { it in h } } }
                .sortedWith(compareBy<Recipe> { if (query.isNotBlank() && Catalog.norm(it.displayName(lang)).startsWith(Catalog.norm(query))) 0 else 1 }
                    .thenBy { when (it.source) { "custom" -> 0; "local" -> 1; "online" -> 2; else -> 3 } }
                    .thenBy { it.displayName(lang) })
                .toList()
        }
    }
    LaunchedEffect(query) { vm.searchOnline(query) }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp + de.schnubbirunkel.barkeep.ui.LocalBottomBarSpace.current)) {
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, null) } },
                singleLine = true, shape = RoundedCornerShape(18.dp),
            )
            de.schnubbirunkel.barkeep.ui.SettingsButton(nav)
            }
            ChipRow {
                FilterChip(type.isEmpty(), { type = "" }, { Text(stringResource(R.string.type_all)) })
                listOf("cocktail", "mocktail", "punch", "smoothie", "shot").forEach { t ->
                    FilterChip(type == t, { type = if (type == t) "" else t }, { Text(typeLabel(t)) })
                }
            }
            ChipRow {
                Labels.spirit.keys.filter { it != "liqueur" }.forEach { s ->
                    FilterChip(spirit == s, { spirit = if (spirit == s) "" else s }, { Text(stringResource(Labels.spirit.getValue(s))) })
                }
            }
            ChipRow {
                FilterChip(onlyMakeable, { onlyMakeable = !onlyMakeable }, { Text(stringResource(R.string.only_makeable)) })
                SEARCH_STYLES.forEach { s ->
                    FilterChip(style == s, { style = if (style == s) "" else s }, { Text(styleLabels.getValue(s)) })
                }
            }
            Text(stringResource(R.string.results, results.size), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        if (results.isEmpty() && online.isEmpty()) item { EmptyHint(stringResource(R.string.no_results)) }
        items(results.take(400), key = { it.id }) { r ->
            RecipeRow(r, vm.catalog, lang, { nav.navigate(Routes.recipe(r.id)) }, Modifier.animateItem(), "search") {
                when (r.source) {
                    "custom" -> Pill(stringResource(R.string.custom_badge), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    "local" -> {}
                    else -> if (lang == "de" && r.steps["de"].isNullOrEmpty()) Pill("EN", de.schnubbirunkel.barkeep.ui.glassFill(true), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (online.isNotEmpty() && type.isEmpty() && style.isEmpty() && spirit.isEmpty() && !onlyMakeable) {
            item { SectionTitle(stringResource(R.string.online_results)) }
            items(online, key = { "o" + it.id }) { r ->
                RecipeRow(r, vm.catalog, lang, { nav.navigate(Routes.recipe(r.id)) }, Modifier.animateItem(), "online") {
                    Pill(stringResource(R.string.online_badge), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        if (onlineError && query.length > 1 && user.settings.useOnline) item { EmptyHint(stringResource(R.string.online_error)) }
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
        Spacer(Modifier.width(8.dp))
    }
}
