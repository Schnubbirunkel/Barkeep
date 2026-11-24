@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schnubbirunkel.barkeep.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.Catalog
import de.schnubbirunkel.barkeep.data.Discovery
import de.schnubbirunkel.barkeep.data.Matcher
import de.schnubbirunkel.barkeep.data.Recipe
import de.schnubbirunkel.barkeep.data.RecipeSource
import de.schnubbirunkel.barkeep.data.SourceIds
import de.schnubbirunkel.barkeep.data.Units
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.DrinkArt
import de.schnubbirunkel.barkeep.ui.Ink
import de.schnubbirunkel.barkeep.ui.glass
import de.schnubbirunkel.barkeep.ui.isDarkUi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.graphicsLayer
import de.schnubbirunkel.barkeep.ui.Labels
import de.schnubbirunkel.barkeep.ui.LocalSnackbar
import de.schnubbirunkel.barkeep.ui.Pill
import de.schnubbirunkel.barkeep.ui.RecipeTile
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.SectionTitle
import de.schnubbirunkel.barkeep.ui.appLang
import de.schnubbirunkel.barkeep.ui.ingredientAmount
import de.schnubbirunkel.barkeep.ui.parseColor
import de.schnubbirunkel.barkeep.ui.typeLabel
import de.schnubbirunkel.barkeep.ui.unitLabeler
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(vm: AppViewModel, nav: NavController, id: String) {
    var recipe by remember(id) { mutableStateOf(vm.recipe(id)) }
    var loading by remember(id) { mutableStateOf(recipe == null || recipe!!.ingredients.isEmpty()) }
    LaunchedEffect(id) {
        if (loading) { recipe = vm.loadRecipe(id); loading = false }
    }
    val r = recipe
    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator() else Text(stringResource(R.string.no_network))
        }
        return
    }
    RecipeDetail(vm, nav, r)
}

@Composable
private fun RecipeDetail(vm: AppViewModel, nav: NavController, base: Recipe) {
    val lang = appLang()
    val ctx = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val user by vm.user.collectAsState()
    val all by vm.allRecipes.collectAsState()
    val catalog = vm.catalog
    val unit = unitLabeler()
    var servings by remember(base.id) { mutableIntStateOf(base.servings.coerceAtLeast(1)) }
    var volumeFactor by remember(base.id) { mutableDoubleStateOf(0.0) }
    var showVolume by remember { mutableStateOf(false) }
    var stepMode by remember { mutableStateOf(false) }
    var subsFor by remember { mutableStateOf<String?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val heroState = de.schnubbirunkel.barkeep.ui.LocalHeroKey.current
    val heroKey = remember(base.id) { heroState.value?.takeIf { it.endsWith("/" + base.id) } }
    val factor = if (volumeFactor > 0) volumeFactor else servings.toDouble() / base.servings.coerceAtLeast(1)
    val r = Units.scale(base, factor)
    val match = Matcher.match(base, user.available, catalog)
    val isFav = user.folders.values.any { base.id in it }
    val accent = parseColor(base.color)
    val steps = r.stepsFor(lang)
    val addedMsg = stringResource(R.string.added_to_list)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { vm.toggleFavorite(base) }) {
                        Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, stringResource(R.string.favorite),
                            tint = if (isFav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { folderDialog = true }) { Icon(Icons.Filled.FolderOpen, stringResource(R.string.save_to_folder)) }
                    IconButton(onClick = {
                        val text = shareText(r, catalog, lang, user.settings.unitSystem, unit)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))
                    }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                    if (base.source == RecipeSource.CUSTOM) {
                        IconButton(onClick = { nav.navigate(Routes.edit(id = base.id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    } else {
                        IconButton(onClick = { nav.navigate(Routes.edit(base = base.id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.customize)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)),
            )
        },
    ) { pad ->
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), MaterialTheme.colorScheme.background)))
                    .padding(top = pad.calculateTopPadding()), contentAlignment = Alignment.Center) {
                    DrinkArt(
                        base,
                        Modifier.padding(top = 8.dp, bottom = 12.dp).fillMaxWidth(0.74f).aspectRatio(1f)
                            .graphicsLayer {
                                // gentle parallax while scrolling away
                                val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                                translationY = off * 0.35f
                                alpha = 1f - (off / 1400f).coerceIn(0f, 0.6f)
                            },
                        corner = 32.dp, sharedKey = heroKey,
                    )
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(base.displayName(lang), style = MaterialTheme.typography.headlineLarge)
                    if (base.nameDe != null && lang == "de" && base.nameDe != base.name)
                        Text(base.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val chip = de.schnubbirunkel.barkeep.ui.glassFill(true)
                        val on = MaterialTheme.colorScheme.onSurface
                        Pill(typeLabel(base.type), chip, on)
                        if (!base.isAlcoholic) Pill(stringResource(R.string.alcohol_free), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        Pill(stringResource(when (base.difficulty) { 3 -> R.string.difficulty_3; 2 -> R.string.difficulty_2; else -> R.string.difficulty_1 }), chip, on)
                        Pill(catalog.glass(base.glass, lang), chip, on)
                        base.flavors.take(4).forEach { f -> Labels.flavor[f]?.let { Pill(stringResource(it), chip, MaterialTheme.colorScheme.onSurfaceVariant) } }
                        if (base.source == RecipeSource.CUSTOM) Pill(stringResource(R.string.custom_badge), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    base.about?.get(lang)?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // ---- scaling & units ----
            item {
                SectionTitle(stringResource(R.string.ingredients))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(de.schnubbirunkel.barkeep.ui.glassFill(true)), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { volumeFactor = 0.0; servings = (servings - 1).coerceAtLeast(1) }) { Icon(Icons.Filled.Remove, null) }
                        Text(if (volumeFactor > 0) "×" + Units.fraction(volumeFactor, 4) else "$servings", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { volumeFactor = 0.0; servings = (servings + 1).coerceAtMost(200) }) { Icon(Icons.Filled.Add, null) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.servings), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showVolume = true }) { Text(stringResource(R.string.batch)) }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(de.schnubbirunkel.barkeep.ui.glassFill())) {
                        listOf("ml", "cl", "oz").forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = user.settings.unitSystem == u,
                                onClick = { vm.updateSettings { it.copy(unitSystem = u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(u) }
                        }
                    }
                }
                val vol = Units.liquidVolumeMl(r)
                if (vol > 0) Text(stringResource(R.string.total_volume, ingredientAmount(de.schnubbirunkel.barkeep.data.RecipeIngredient(amount = vol, unit = "ml"), user.settings.unitSystem, unit)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(r.ingredients.size) { idx ->
                val ing = r.ingredients[idx]
                val iid = catalog.idOf(ing)
                val have = iid in Catalog.PANTRY || iid !in match.missing
                val swap = match.swaps[iid]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)
                        .glass(RoundedCornerShape(if (idx == 0) 22.dp else 10.dp, if (idx == 0) 22.dp else 10.dp, if (idx == r.ingredients.lastIndex) 22.dp else 10.dp, if (idx == r.ingredients.lastIndex) 22.dp else 10.dp))
                        .clickable { subsFor = iid }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (have) Icons.Filled.CheckCircle else if (swap != null) Icons.Filled.SwapHoriz else Icons.Filled.RadioButtonUnchecked, null,
                        tint = if (have) MaterialTheme.colorScheme.secondary else if (swap != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(ingredientAmount(ing, user.settings.unitSystem, unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(96.dp))
                    Column(Modifier.weight(1f)) {
                        Text(catalog.name(ing, lang), style = MaterialTheme.typography.bodyLarge)
                        if (swap != null) Text("→ " + catalog.name(swap, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (catalog.substitutes(iid).isNotEmpty()) Icon(Icons.Filled.SwapHoriz, stringResource(R.string.substitutes), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            item {
                base.garnish?.get(lang)?.let {
                    Text(stringResource(R.string.garnish) + ": " + it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                if (match.missing.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            vm.addMissingToShopping(match.missing.filter { it !in match.swaps }.ifEmpty { match.missing }, lang)
                            scope.launch { snackbar.showSnackbar(addedMsg) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.AddShoppingCart, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_missing_to_list) + " (" + match.missing.size + ")")
                    }
                }
            }
            // ---- steps ----
            if (steps.isNotEmpty()) item {
                SectionTitle(stringResource(R.string.steps), action = {
                    TextButton(onClick = { stepMode = true }) { Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.step_mode)) }
                })
                steps.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).glass(RoundedCornerShape(20.dp)).padding(14.dp)) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                if (lang == "de" && base.steps["de"].isNullOrEmpty() && base.source != RecipeSource.CUSTOM)
                    Text(stringResource(R.string.english_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                if (user.settings.beginnerMode) techniqueTip(base.method, steps)?.let { tip ->
                    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.padding(14.dp)) {
                            Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.food_pairing))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Discovery.pairings(base, catalog, lang).forEach { Pill(it, de.schnubbirunkel.barkeep.ui.glassFill(true), MaterialTheme.colorScheme.onSurface) }
                }
            }
            item {
                val similar = remember(base.id, all.size) { Discovery.similar(base, all, catalog) }
                if (similar.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.similar))
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(similar, key = { it.id }) { s -> RecipeTile(s, lang, { nav.navigate(Routes.recipe(s.id)) }, section = "similar") }
                    }
                }
                val credit = when {
                    base.id.startsWith("cdb:") -> R.string.source_cdb_credit
                    base.source == SourceIds.COMMUNITY -> R.string.source_community_credit
                    else -> null
                }
                if (credit != null) Text(stringResource(credit), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                base.basedOn?.let { b -> vm.recipe(b)?.let {
                    Text(stringResource(R.string.based_on, it.displayName(lang)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).clickable { nav.navigate(Routes.recipe(b)) })
                } }
                if (base.notes.isNotBlank()) Text(base.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
            }
        }
    }

    if (showVolume) {
        var text by remember { mutableStateOf("1000") }
        val baseVol = Units.liquidVolumeMl(base)
        AlertDialog(
            onDismissRequest = { showVolume = false },
            title = { Text(stringResource(R.string.scale_volume)) },
            text = {
                Column {
                    OutlinedTextField(text, { text = it.filter { c -> c.isDigit() } }, label = { Text(stringResource(R.string.target_volume)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.batch_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = text.toDoubleOrNull() ?: 0.0
                    if (t > 0 && baseVol > 0) volumeFactor = t / baseVol
                    showVolume = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showVolume = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    subsFor?.let { iid ->
        val subs = catalog.substitutes(iid)
        AlertDialog(
            onDismissRequest = { subsFor = null },
            title = { Text(catalog.name(iid, lang)) },
            text = {
                Column {
                    Text(stringResource(R.string.substitutes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    if (subs.isEmpty()) Text(stringResource(R.string.no_substitutes))
                    subs.forEach { s ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(catalog.name(s.key, lang), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                if (s.key in user.available) { Spacer(Modifier.width(6.dp)); Pill(stringResource(R.string.substitute_have), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Text(if (lang == "de") s.de else s.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (iid !in Catalog.PANTRY) TextButton(onClick = { vm.addShopping(catalog.name(iid, lang), iid); subsFor = null; scope.launch { snackbar.showSnackbar(addedMsg) } }) {
                        Icon(Icons.Filled.AddShoppingCart, null)
                    }
                    TextButton(onClick = { subsFor = null }) { Text(stringResource(R.string.close)) }
                }
            },
        )
    }

    if (folderDialog) FolderDialog(vm, base) { folderDialog = false }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_recipe_q)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCustom(base.id); nav.popBackStack() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )

    if (stepMode && steps.isNotEmpty()) StepByStep(base.displayName(lang), steps, accent) { stepMode = false }
}

private fun techniqueTip(method: String, steps: List<String>): Int? = when (method) {
    "shake_up", "shake_rocks", "shake_top", "crushed", "shake_crushed" -> R.string.tip_shake
    "stir_up", "stir_rocks" -> R.string.tip_stir
    "dry_shake" -> R.string.tip_dry
    "muddle" -> R.string.tip_muddle
    "build", "build_noice" -> R.string.tip_build
    "layer" -> R.string.tip_layer
    "blend", "blend_noice" -> R.string.tip_blend
    else -> {
        val t = steps.joinToString(" ").lowercase()
        when {
            "dry shake" in t -> R.string.tip_dry
            "shake" in t || "schüttel" in t -> R.string.tip_shake
            "stir" in t || "rühr" in t -> R.string.tip_stir
            "muddle" in t -> R.string.tip_muddle
            "blend" in t -> R.string.tip_blend
            else -> null
        }
    }
}

private fun shareText(r: Recipe, catalog: Catalog, lang: String, unitSystem: String, unit: (String) -> String): String = buildString {
    appendLine(r.displayName(lang))
    appendLine()
    r.ingredients.forEach { appendLine("• " + ingredientAmount(it, unitSystem, unit) + " " + catalog.name(it, lang)) }
    appendLine()
    r.stepsFor(lang).forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
    appendLine()
    append("— Barkeep")
}

@Composable
fun FolderDialog(vm: AppViewModel, recipe: Recipe, onDismiss: () -> Unit) {
    val user by vm.user.collectAsState()
    var newName by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.default_folder)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_to_folder)) },
        text = {
            Column {
                user.folders.keys.forEach { f ->
                    val inIt = recipe.id in user.folders[f].orEmpty()
                    Row(Modifier.fillMaxWidth().clickable { vm.setInFolder(f, recipe, !inIt) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(inIt, { vm.setInFolder(f, recipe, it) })
                        Text(if (f == de.schnubbirunkel.barkeep.data.UserData.DEFAULT_FOLDER) defaultName else f)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.new_folder)) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (newName.isNotBlank()) { vm.createFolder(newName); vm.setInFolder(newName.trim(), recipe, true); newName = "" }
                    }) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun StepByStep(title: String, steps: List<String>, accent: androidx.compose.ui.graphics.Color, onClose: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(if (isDarkUi()) Ink else androidx.compose.ui.graphics.Color(0xFFF2F3F8)).padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { (i + 1f) / steps.size }, modifier = Modifier.fillMaxWidth(), color = accent)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.step_x_of_y, i + 1, steps.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(steps[i], style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (i > 0) i-- }, enabled = i > 0, modifier = Modifier.weight(1f).height(56.dp)) { Text(stringResource(R.string.previous)) }
                Button(onClick = { if (i < steps.size - 1) i++ else onClose() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(if (i < steps.size - 1) R.string.next else R.string.done))
                }
            }
        }
    }
}
