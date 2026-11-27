@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schnubbirunkel.barkeep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.Finder
import de.schnubbirunkel.barkeep.data.Wish
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.DrinkArt
import de.schnubbirunkel.barkeep.ui.EmptyHint
import de.schnubbirunkel.barkeep.ui.Labels
import de.schnubbirunkel.barkeep.ui.Pill
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.appLang
import kotlinx.coroutines.launch

private const val STEPS = 6

@Composable
fun FinderScreen(vm: AppViewModel, nav: NavController) {
    val lang = appLang()
    val strings = de.schnubbirunkel.barkeep.ui.rememberStrings()
    val user by vm.user.collectAsState()
    val all by vm.allRecipes.collectAsState()
    val scope = rememberCoroutineScope()
    var wish by remember { mutableStateOf(Wish()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var aiText by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val noKey = stringResource(R.string.finder_ai_no_key)

    fun next() {
        step = if (step == 1 && wish.alcohol == "no") 3 else step + 1
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.finder_title)) },
            navigationIcon = { IconButton(onClick = { if (step > 0) step = if (step == 3 && wish.alcohol == "no") 1 else step - 1 else nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { if (step > 0) IconButton(onClick = { wish = Wish(); step = 0 }) { Icon(Icons.Filled.Refresh, stringResource(R.string.finder_restart)) } },
        )
    }) { pad ->
        if (step >= STEPS) {
            val res = remember(wish, all, user.available) {
                Finder.suggest(wish, all, user.available, vm.catalog, lang, { key -> labelFor(strings, key) })
            }
            LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp)) {
                item {
                    Text(stringResource(R.string.finder_results), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
                }
                if (res.isEmpty()) item { EmptyHint(stringResource(R.string.finder_none)) }
                items(res, key = { it.recipe.id }) { sug ->
                    Card(border = de.schnubbirunkel.barkeep.ui.glassBorder(),
                        onClick = { nav.navigate(Routes.recipe(sug.recipe.id)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = de.schnubbirunkel.barkeep.ui.glassFill()),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            DrinkArt(sug.recipe, Modifier.size(72.dp), corner = 14.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sug.recipe.displayName(lang), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sug.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { sug.percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Pill(stringResource(R.string.finder_match, sug.percent), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        } else Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            val (title, multi) = when (step) {
                0 -> R.string.finder_q_alcohol to false
                1 -> R.string.finder_q_flavor to true
                2 -> R.string.finder_q_spirit to true
                3 -> R.string.finder_q_strength to false
                4 -> R.string.finder_q_occasion to true
                else -> R.string.finder_q_bar to false
            }
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
            if (multi) Text(stringResource(R.string.finder_multi), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                @Composable
                fun Choice(label: String, selected: Boolean, onClick: () -> Unit) =
                    FilterChip(selected, onClick, { Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) },
                        shape = RoundedCornerShape(16.dp))
                when (step) {
                    0 -> listOf("yes" to R.string.finder_alc_yes, "no" to R.string.finder_alc_no, "any" to R.string.finder_any).forEach { (v, l) ->
                        Choice(stringResource(l), wish.alcohol == v) { wish = wish.copy(alcohol = v); next() }
                    }
                    1 -> Finder.FLAVORS.forEach { f ->
                        Choice(stringResource(Labels.flavor.getValue(f)).replaceFirstChar { it.uppercase() }, f in wish.flavors) {
                            wish = wish.copy(flavors = if (f in wish.flavors) wish.flavors - f else wish.flavors + f)
                        }
                    }
                    2 -> Finder.SPIRIT_GROUPS.keys.forEach { g ->
                        Choice(stringResource(Labels.spirit.getValue(g)), g in wish.spirits) {
                            wish = wish.copy(spirits = if (g in wish.spirits) wish.spirits - g else wish.spirits + g)
                        }
                    }
                    3 -> listOf(1 to R.string.finder_strength_1, 2 to R.string.finder_strength_2, 3 to R.string.finder_strength_3, 0 to R.string.finder_any).forEach { (v, l) ->
                        Choice(stringResource(l), wish.strength == v) { wish = wish.copy(strength = v); next() }
                    }
                    4 -> Finder.OCCASIONS.keys.forEach { o ->
                        Choice(stringResource(Labels.occasion.getValue(o)), o in wish.occasions) {
                            wish = wish.copy(occasions = if (o in wish.occasions) wish.occasions - o else wish.occasions + o)
                        }
                    }
                    else -> listOf(-1 to R.string.finder_bar_any, 0 to R.string.finder_bar_0, 1 to R.string.finder_bar_1).forEach { (v, l) ->
                        Choice(stringResource(l), wish.maxMissing == v) { wish = wish.copy(maxMissing = v); step = STEPS }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (multi) Button(onClick = { next() }) { Text(stringResource(R.string.next)) }
                TextButton(onClick = { next() }) { Text(stringResource(R.string.finder_skip)) }
                if (step > 0) TextButton(onClick = { step = STEPS }) { Text(stringResource(R.string.finder_show)) }
            }

            if (step == 0) {
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.finder_ai_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(aiText, { aiText = it }, placeholder = { Text(stringResource(R.string.finder_ai_hint)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(enabled = aiText.isNotBlank() && !aiBusy, onClick = {
                    aiBusy = true; aiError = null
                    scope.launch {
                        vm.parseWish(aiText).onSuccess { wish = it; step = STEPS }
                            .onFailure { aiError = if (it.message == "NO_KEY") noKey else (it.message ?: "?") }
                        aiBusy = false
                    }
                }) {
                    if (aiBusy) CircularProgressIndicator(Modifier.size(18.dp)) else Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.finder_ai_go))
                }
                aiError?.let { Text(de.schnubbirunkel.barkeep.ui.aiErrorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (user.settings.aiKey.isBlank() && user.settings.aiProvider != "custom") Text(noKey, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun labelFor(strings: (Int) -> String, key: String): String {
    val (prefix, k) = key.substringBefore('_') to key.substringAfter('_')
    val id = when (prefix) {
        "flavor" -> Labels.flavor[k]
        "spirit" -> Labels.spirit[k]
        "occasion" -> Labels.occasion[k]
        else -> if (key == "can_make") R.string.can_make else null
    }
    return id?.let { strings(it) } ?: k
}
