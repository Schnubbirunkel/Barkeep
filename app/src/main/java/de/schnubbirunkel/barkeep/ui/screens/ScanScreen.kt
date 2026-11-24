@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schnubbirunkel.barkeep.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.ScanState
import de.schnubbirunkel.barkeep.ui.appLang
import java.io.File
import kotlin.math.roundToInt

private fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()

@Composable
fun ScanScreen(vm: AppViewModel, nav: NavController, initialMode: String) {
    val ctx = LocalContext.current
    val lang = appLang()
    val state by vm.scan.collectAsState()
    var ingredientsMode by remember { mutableStateOf(initialMode == "ingredients") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val photoFile = remember { File(ctx.cacheDir, "scan.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", photoFile) }
    DisposableEffect(Unit) { onDispose { vm.resetScan() } }

    fun analyse(b: Bitmap?) {
        if (b == null) return
        bitmap = b
        vm.scanImage(b, ingredientsMode, lang)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) analyse(loadBitmap(ctx, photoUri)) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) analyse(loadBitmap(ctx, uri)) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.scan_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!ingredientsMode, { ingredientsMode = false; vm.resetScan() }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.scan_mode_cocktail)) }
                SegmentedButton(ingredientsMode, { ingredientsMode = true; vm.resetScan() }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.scan_mode_ingredients)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(if (ingredientsMode) R.string.scan_ingredients_sub else R.string.scan_cocktail_sub), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { camera.launch(photoUri) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.take_photo)) }
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.pick_photo))
                }
            }
            Spacer(Modifier.height(16.dp))
            bitmap?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)))
                Spacer(Modifier.height(16.dp))
            }
            when (val s = state) {
                ScanState.Idle -> Text(stringResource(R.string.scan_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScanState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.scan_working))
                }
                is ScanState.Error -> {
                    if (s.message == "NO_KEY") {
                        Text(stringResource(R.string.scan_no_key))
                        FilledTonalButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text(stringResource(R.string.settings)) }
                    } else Text(stringResource(R.string.scan_error, de.schnubbirunkel.barkeep.ui.aiErrorText(s.message)), color = MaterialTheme.colorScheme.error)
                }
                is ScanState.Cocktail -> CocktailResult(vm, nav, s, lang)
                is ScanState.Ingredients -> IngredientResult(vm, nav, s.ids, lang)
            }
        }
    }
}

@Composable
private fun CocktailResult(vm: AppViewModel, nav: NavController, s: ScanState.Cocktail, lang: String) {
    val g = s.guess
    if (!g.isDrink) { Text(stringResource(R.string.scan_not_drink)); return }
    Card(colors = CardDefaults.cardColors(containerColor = de.schnubbirunkel.barkeep.ui.glassFill())) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.scan_found, s.match?.displayName(lang) ?: g.name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.scan_confidence, (g.confidence * 100).roundToInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (g.history.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.history), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text(g.history) }
            if (g.ingredients.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                g.ingredients.forEach { i -> Text("• " + (i.amount?.let { a -> de.schnubbirunkel.barkeep.data.Units.format(a, i.unit.ifBlank { "ml" }) + " " + i.unit + " " } ?: "") + i.name) }
            }
            if (g.steps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.steps), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                g.steps.forEachIndexed { i, st -> Text("${i + 1}. $st") }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.match?.let { m -> Button(onClick = { nav.navigate(Routes.recipe(m.id)) }) { Text(stringResource(R.string.scan_open_recipe)) } }
                OutlinedButton(onClick = {
                    val r = vm.guessToRecipe(g, lang)
                    vm.saveCustom(r)
                    nav.navigate(Routes.recipe(r.id))
                }) { Text(stringResource(R.string.scan_save_recipe)) }
            }
        }
    }
}

@Composable
private fun IngredientResult(vm: AppViewModel, nav: NavController, ids: List<String>, lang: String) {
    var selected by remember(ids) { mutableStateOf(ids.toSet()) }
    Text(stringResource(R.string.scan_found_ingredients), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ids.forEach { id ->
            FilterChip(id in selected, { selected = if (id in selected) selected - id else selected + id }, { Text(vm.catalog.name(id, lang)) })
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(enabled = selected.isNotEmpty(), onClick = { vm.addInventory(selected); nav.navigate(Routes.bar(2)) }) {
        Text(stringResource(R.string.scan_add_to_bar))
    }
}
