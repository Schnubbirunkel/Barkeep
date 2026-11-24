@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schnubbirunkel.barkeep.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.Bottle
import de.schnubbirunkel.barkeep.data.Catalog
import de.schnubbirunkel.barkeep.data.Matcher
import de.schnubbirunkel.barkeep.data.MatchResult
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.BarcodeState
import de.schnubbirunkel.barkeep.ui.EmptyHint
import de.schnubbirunkel.barkeep.ui.BottleDrawing
import de.schnubbirunkel.barkeep.ui.Labels
import de.schnubbirunkel.barkeep.ui.LocalSnackbar
import de.schnubbirunkel.barkeep.ui.Pill
import de.schnubbirunkel.barkeep.ui.RecipeRow
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.SectionTitle
import de.schnubbirunkel.barkeep.ui.appLang
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color

@Composable
fun BarScreen(vm: AppViewModel, nav: NavController, initialTab: Int) {
    var tab by rememberSaveable { mutableIntStateOf(if (initialTab >= 2) 1 else 0) }
    Column(Modifier.statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_bar), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            de.schnubbirunkel.barkeep.ui.SettingsButton(nav)
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf(R.string.tab_stock, R.string.tab_make).forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
        if (tab == 0) StockTab(vm, nav) else MakeTab(vm, nav)
    }
}

// ------------------------------------------------------------------ stock (bottles + ingredients in one list)
@Composable
private fun StockTab(vm: AppViewModel, nav: NavController) {
    val lang = appLang()
    val ctx = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val user by vm.user.collectAsState()
    val barcode by vm.barcode.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var manualFor by remember { mutableStateOf<String?>(null) } // "" = any ingredient
    var editing by remember { mutableStateOf<Bottle?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    val unavailable = stringResource(R.string.barcode_unavailable)
    val catalog = vm.catalog

    fun startScan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let { vm.onBarcode(it) } }
            .addOnFailureListener { scope.launch { snackbar.showSnackbar(unavailable) } }
    }

    val matches = remember(query) { catalog.search(query) }
    val q = Catalog.spelling(Catalog.norm(query))
    val owned = (user.inventory + user.bottles.map { it.ingredientId })
        .filter { q.isEmpty() || it in matches || (it.startsWith("x:") && q in Catalog.spelling(it)) }
    val bottlesBy = user.bottles.groupBy { it.ingredientId }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + de.schnubbirunkel.barkeep.ui.LocalBottomBarSpace.current)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard(Icons.Filled.QrCodeScanner, stringResource(R.string.scan_barcode), "EAN / UPC", Modifier.weight(1f)) { startScan() }
                ActionCard(Icons.Filled.Add, stringResource(R.string.add_bottle), stringResource(R.string.bar_search), Modifier.weight(1f)) { manualFor = "" }
                ActionCard(Icons.Filled.CameraAlt, stringResource(R.string.scan_ingredients), "AI", Modifier.weight(1f)) { nav.navigate(Routes.scan("ingredients")) }
            }
            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(stringResource(R.string.bar_search)) },
                singleLine = true, shape = RoundedCornerShape(18.dp),
            )
        }
        // ---- what I have ----
        item(key = "have") { SectionTitle(stringResource(R.string.stock_have, user.available.size)) }
        if (user.available.isEmpty()) item { EmptyHint(stringResource(R.string.bar_empty)) }
        val ownedByCat = owned.sortedBy { catalog.name(it, lang) }.groupBy { catalog.category(it) }
        Catalog.CATEGORY_ORDER.forEach { cat ->
            val ids = ownedByCat[cat] ?: return@forEach
            item(key = "oc$cat") {
                Text(stringResource(Labels.cat.getValue(cat)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            }
            items(ids, key = { "o$it" }) { id ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { removing = id }) { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary) }
                        Text(catalog.name(id, lang), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { manualFor = id }) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Text(stringResource(R.string.stock_add_bottle_for)) }
                    }
                    bottlesBy[id].orEmpty().forEach { b -> BottleRow(b, catalog, lang) { editing = b } }
                }
            }
        }
        // ---- add more ----
        item(key = "add") { SectionTitle(stringResource(R.string.stock_add_more)) }
        val notOwned = catalog.ingredients.filter { it.key !in Catalog.PANTRY && it.key !in user.available && it.key in matches }
        if (query.isNotBlank() && notOwned.isEmpty() && owned.isEmpty()) item {
            Text(stringResource(R.string.stock_no_match, query.trim()), modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (query.isNotBlank() && catalog.resolve(query) == null) item {
            TextButton(onClick = { vm.toggleInventory(catalog.freeId(query.trim())); query = "" }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.Add, null); Text(stringResource(R.string.bar_add_custom, query.trim()))
            }
        }
        val notOwnedByCat = notOwned.groupBy { it.category }
        Catalog.CATEGORY_ORDER.forEach { cat ->
            val list = notOwnedByCat[cat]?.sortedBy { if (lang == "de") it.de else it.en } ?: return@forEach
            item(key = "nc$cat") {
                Text(stringResource(Labels.cat.getValue(cat)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { c ->
                        FilterChip(false, { vm.toggleInventory(c.key) }, { Text(if (lang == "de") c.de else c.en) },
                            leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) })
                    }
                }
            }
        }
    }

    when (val s = barcode) {
        BarcodeState.Idle -> {}
        BarcodeState.Looking -> AlertDialog(
            onDismissRequest = { vm.resetBarcode() },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)); Text(stringResource(R.string.barcode_looking)) } },
            confirmButton = {},
        )
        is BarcodeState.Result -> BottleDialog(
            vm, title = stringResource(if (s.product != null) R.string.barcode_found else R.string.add_bottle),
            info = if (s.product == null) stringResource(R.string.barcode_unknown, s.barcode) else if (s.ingredientId == null) stringResource(R.string.barcode_pick_hint) else null,
            initialName = s.product?.name.orEmpty(), initialBrand = s.product?.brand.orEmpty(), initialIngredient = s.ingredientId,
            imageUrl = s.product?.imageUrl,
            onDismiss = { vm.resetBarcode() },
        ) { name, brand, ing ->
            vm.addBottle(name, brand, ing, s.barcode, s.product?.quantityMl, s.product?.imageUrl); vm.resetBarcode()
        }
        is BarcodeState.Error -> {
            val msg = stringResource(R.string.barcode_error, s.message)
            androidx.compose.runtime.LaunchedEffect(s) { snackbar.showSnackbar(msg); vm.resetBarcode(); manualFor = "" }
        }
    }
    manualFor?.let { forId ->
        BottleDialog(vm, stringResource(R.string.add_bottle), null, "", "", forId.ifEmpty { null }, null, { manualFor = null }) { n, b, i ->
            vm.addBottle(n, b, i); manualFor = null
        }
    }
    editing?.let { b -> EditBottleDialog(vm, b) { editing = null } }
    removing?.let { id ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.stock_remove_q, catalog.name(id, lang))) },
            text = if (bottlesBy[id].isNullOrEmpty()) null else ({ Text(stringResource(R.string.stock_remove_bottles)) }),
            confirmButton = { TextButton(onClick = { vm.removeFromBar(id); removing = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BottleRow(b: Bottle, catalog: Catalog, lang: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) {
            if (b.imageUrl != null) AsyncImage(b.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
            else BottleDrawing(fillColor(b.fill), b.fill, Modifier.size(44.dp).padding(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(listOf(b.brand, b.name).filter { it.isNotBlank() }.distinct().joinToString(" · "), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(catalog.name(b.ingredientId, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Pill("${b.fill}%", de.schnubbirunkel.barkeep.ui.glassFill(true), if (b.fill <= 15) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
    }
}

private fun fillColor(fill: Int) = when {
    fill <= 15 -> Color(0xFFFF6F61)
    fill <= 40 -> Color(0xFFF4C542)
    else -> Color(0xFF5ED6B0)
}

@Composable
private fun EditBottleDialog(vm: AppViewModel, b: Bottle, onDismiss: () -> Unit) {
    val lang = appLang()
    var fill by remember { mutableFloatStateOf(b.fill.toFloat()) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.updateBottle(b.copy(fill = fill.toInt())); onDismiss() },
        title = { Text(listOf(b.brand, b.name).filter { it.isNotBlank() }.distinct().joinToString(" ")) },
        text = {
            Column {
                Text(stringResource(R.string.bottle_counts_as) + ": " + vm.catalog.name(b.ingredientId, lang))
                Spacer(Modifier.padding(6.dp))
                Text(stringResource(R.string.bottle_fill, fill.toInt()))
                Slider(fill, { fill = it }, valueRange = 0f..100f, steps = 19)
                FilledTonalButton(onClick = { vm.bottleEmpty(b); onDismiss() }) {
                    Icon(Icons.Filled.AddShoppingCart, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.bottle_empty_action))
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.updateBottle(b.copy(fill = fill.toInt())); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) } },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.bottle_remove_q)) },
        confirmButton = { TextButton(onClick = { vm.removeBottle(b.id); confirm = false; onDismiss() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun BottleDialog(
    vm: AppViewModel, title: String, info: String?, initialName: String, initialBrand: String, initialIngredient: String?, imageUrl: String?,
    onDismiss: () -> Unit, onSave: (name: String, brand: String, ingredientId: String) -> Unit,
) {
    val lang = appLang()
    var name by remember { mutableStateOf(initialName) }
    var brand by remember { mutableStateOf(initialBrand) }
    var ing by remember { mutableStateOf(initialIngredient) }
    var picking by remember { mutableStateOf(initialIngredient == null && info != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (imageUrl != null) AsyncImage(imageUrl, null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.padding(4.dp)) }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.bottle_name)) }, singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.bottle_brand)) }, singleLine = true)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.bottle_counts_as), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (picking || ing == null) {
                    IngredientPicker(vm, initialQuery = name) { ing = it; picking = false }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.catalog.name(ing!!, lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.edit)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ing != null, onClick = { ing?.let { onSave(name.trim(), brand.trim(), it) } }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Searchable catalogue list; also allows free text. */
@Composable
fun IngredientPicker(vm: AppViewModel, initialQuery: String = "", onPick: (String) -> Unit) {
    val lang = appLang()
    var q by remember { mutableStateOf("") }
    val list = remember(q) {
        val m = vm.catalog.search(q)
        vm.catalog.ingredients.filter { it.key in m }.take(60)
    }
    Column {
        OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(stringResource(R.string.bar_search)) }, singleLine = true)
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(list, key = { it.key }) { c ->
                Text(if (lang == "de") c.de else c.en, modifier = Modifier.fillMaxWidth().clickable { onPick(c.key) }.padding(vertical = 10.dp, horizontal = 4.dp))
            }
            if (q.isNotBlank() && list.none { Catalog.norm(it.en) == Catalog.norm(q) || Catalog.norm(it.de) == Catalog.norm(q) }) item {
                Text(stringResource(R.string.bar_add_custom, q.trim()), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(vm.catalog.freeId(q.trim())) }.padding(vertical = 10.dp, horizontal = 4.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ what can I make
@Composable
private fun MakeTab(vm: AppViewModel, nav: NavController) {
    val lang = appLang()
    val user by vm.user.collectAsState()
    val all by vm.allRecipes.collectAsState()
    val inv = user.available
    val matches = remember(all, inv) { Matcher.all(all, inv, vm.catalog) }
    val ready = matches.filter { it.missingCount == 0 }
    val swaps = matches.filter { it.missingCount > 0 && it.missingAfterSwaps == 0 }
    val one = matches.filter { it.missingCount == 1 && it.missingAfterSwaps == 1 }
    val two = matches.filter { it.missingCount == 2 && it.missingAfterSwaps == 2 }
    val bestBuys = remember(matches) {
        matches.filter { it.missingCount == 1 }.groupingBy { it.missing.first() }.eachCount()
            .entries.sortedByDescending { it.value }.take(8)
    }
    if (inv.isEmpty()) { EmptyHint(stringResource(R.string.bar_empty)); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + de.schnubbirunkel.barkeep.ui.LocalBottomBarSpace.current)) {
        if (bestBuys.isNotEmpty()) item {
            SectionTitle(stringResource(R.string.best_buy))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bestBuys, key = { it.key }) { (id, n) ->
                    Card(border = de.schnubbirunkel.barkeep.ui.glassBorder(),onClick = { vm.addShopping(vm.catalog.name(id, lang), id) }, colors = CardDefaults.cardColors(containerColor = de.schnubbirunkel.barkeep.ui.glassFill())) {
                        Column(Modifier.padding(12.dp).width(140.dp)) {
                            Text(vm.catalog.name(id, lang), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.best_buy_sub, n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Icon(Icons.Filled.AddShoppingCart, null, modifier = Modifier.size(18.dp).align(Alignment.End))
                        }
                    }
                }
            }
        }
        section(R.string.make_now, ready, vm, nav, lang, null)
        section(R.string.make_with_swaps, swaps, vm, nav, lang, MaterialTheme_primary)
        section(R.string.make_one_missing, one, vm, nav, lang, MaterialTheme_tertiary)
        section(R.string.make_two_missing, two, vm, nav, lang, MaterialTheme_tertiary)
    }
}

private const val MaterialTheme_primary = 1
private const val MaterialTheme_tertiary = 2

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int, list: List<MatchResult>, vm: AppViewModel, nav: NavController, lang: String, badgeStyle: Int?,
) {
    if (list.isEmpty()) return
    item(key = "s$title") { SectionTitle(stringResource(title, list.size)) }
    items(list.take(150), key = { "$title-" + it.recipe.id }) { m ->
        RecipeRow(m.recipe, vm.catalog, lang, { nav.navigate(Routes.recipe(m.recipe.id)) }, Modifier.animateItem(), "make$title") {
            if (badgeStyle != null) {
                val text = if (badgeStyle == MaterialTheme_primary) m.swaps.values.joinToString { vm.catalog.name(it, lang) }
                else m.missing.joinToString { vm.catalog.name(it, lang) }
                Pill(text, if (badgeStyle == MaterialTheme_primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    if (badgeStyle == MaterialTheme_primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    Modifier.width(110.dp))
            }
        }
    }
}
