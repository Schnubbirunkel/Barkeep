@file:OptIn(ExperimentalMaterial3Api::class)

package de.schnubbirunkel.barkeep.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.Recipe
import de.schnubbirunkel.barkeep.data.RecipeIngredient
import de.schnubbirunkel.barkeep.data.RecipeSource
import de.schnubbirunkel.barkeep.data.Units
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.appLang
import de.schnubbirunkel.barkeep.ui.typeLabel
import de.schnubbirunkel.barkeep.ui.unitLabeler

private data class EditIng(val id: String?, val amount: String, val unit: String)

private val EDIT_UNITS = listOf("ml", "dash", "tsp", "tbsp", "bsp", "pc", "slice", "leaf", "sprig", "g", "pinch", "scoop")
private val GLASSES = listOf("coupe", "martini", "nick_nora", "rocks", "highball", "collins", "wine", "flute", "hurricane", "tiki", "copper_mug", "mug", "julep", "shot", "pint", "punch_bowl")

@Composable
fun EditorScreen(vm: AppViewModel, nav: NavController, id: String, baseId: String) {
    val lang = appLang()
    val unit = unitLabeler()
    val original = remember { (if (id.isNotEmpty()) vm.recipe(id) else null) }
    val base = remember { original ?: (if (baseId.isNotEmpty()) vm.recipe(baseId) else null) }
    var name by remember { mutableStateOf(base?.displayName(lang)?.let { if (original == null && base != null) it + " ★" else it } ?: "") }
    var type by remember { mutableStateOf(base?.type ?: "cocktail") }
    var glass by remember { mutableStateOf(base?.glass ?: "rocks") }
    var steps by remember { mutableStateOf(base?.stepsFor(lang)?.joinToString("\n") ?: "") }
    var notes by remember { mutableStateOf(base?.notes ?: "") }
    val ings = remember {
        mutableStateListOf<EditIng>().apply {
            base?.ingredients?.forEach { i ->
                val (v, u) = if (i.unit == "ml" || i.unit.isEmpty()) (i.amount ?: 0.0) to (i.unit.ifEmpty { "pc" }) else (i.amount ?: 0.0) to i.unit
                add(EditIng(vm.catalog.idOf(i), if (i.amount == null) "" else Units.format(v, u).replace(" ", "").replace("½", ".5").replace("¼", ".25").replace("¾", ".75").let { s -> if (s.startsWith(".")) "0$s" else s }, u))
            }
            if (isEmpty()) add(EditIng(null, "", "ml"))
        }
    }
    var pickFor by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (original != null) R.string.edit_recipe else R.string.new_recipe)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(enabled = name.isNotBlank() && ings.any { it.id != null }, onClick = {
                        val r = Recipe(
                            id = original?.id ?: ("custom:" + de.schnubbirunkel.barkeep.ui.nowMillis()),
                            name = name.trim(), type = type, glass = glass,
                            styles = base?.styles.orEmpty(), flavors = base?.flavors.orEmpty(), color = base?.color ?: "#E8C77A",
                            method = base?.method.orEmpty(), servings = 1, difficulty = base?.difficulty ?: 1,
                            ingredients = ings.filter { it.id != null }.map { e ->
                                val amt = e.amount.replace(',', '.').toDoubleOrNull()
                                if (e.id!!.startsWith("x:")) RecipeIngredient(name = vm.catalog.name(e.id, lang), amount = amt, unit = e.unit)
                                else RecipeIngredient(key = e.id, amount = amt, unit = e.unit)
                            },
                            garnish = base?.garnish, about = base?.about,
                            steps = steps.lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotEmpty() }.let { mapOf("en" to it, "de" to it) },
                            imageUrl = base?.imageUrl, source = RecipeSource.CUSTOM,
                            basedOn = original?.basedOn ?: base?.takeIf { original == null }?.id, notes = notes.trim(),
                        )
                        vm.saveCustom(r)
                        nav.popBackStack()
                        if (original == null) nav.navigate(Routes.recipe(r.id))
                    }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 40.dp, start = 16.dp, end = 16.dp)) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.recipe_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.type), style = MaterialTheme.typography.labelLarge)
                ChipRowFlat { listOf("cocktail", "mocktail", "punch", "smoothie", "shot").forEach { t -> FilterChip(type == t, { type = t }, { Text(typeLabel(t)) }) } }
                Text(stringResource(R.string.glass), style = MaterialTheme.typography.labelLarge)
                ChipRowFlat { GLASSES.forEach { g -> FilterChip(glass == g, { glass = g }, { Text(vm.catalog.glass(g, lang)) }) } }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium)
            }
            items(ings.size) { i ->
                val e = ings[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(e.amount, { ings[i] = e.copy(amount = it) }, label = { Text(stringResource(R.string.amount)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(84.dp))
                    Spacer(Modifier.width(6.dp))
                    UnitDropdown(e.unit, unit) { ings[i] = e.copy(unit = it) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { pickFor = i }, modifier = Modifier.weight(1f)) {
                        Text(e.id?.let { vm.catalog.name(it, lang) } ?: stringResource(R.string.ingredient), maxLines = 1)
                    }
                    IconButton(onClick = { ings.removeAt(i) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete)) }
                }
            }
            item {
                TextButton(onClick = { ings.add(EditIng(null, "", "ml")) }) { Icon(Icons.Filled.Add, null); Text(stringResource(R.string.add_ingredient)) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(steps, { steps = it }, label = { Text(stringResource(R.string.steps)) }, supportingText = { Text(stringResource(R.string.steps_hint)) },
                    minLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    pickFor?.let { i ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text(stringResource(R.string.bottle_choose_ingredient)) },
            text = { IngredientPicker(vm) { id -> if (i < ings.size) ings[i] = ings[i].copy(id = id); pickFor = null } },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun UnitDropdown(value: String, label: (String) -> String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) { Text(label(value)) }
        DropdownMenu(open, { open = false }) {
            EDIT_UNITS.forEach { u -> DropdownMenuItem(text = { Text(label(u)) }, onClick = { onChange(u); open = false }) }
        }
    }
}

@Composable
private fun ChipRowFlat(content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) { ChipRow(content) }
}
