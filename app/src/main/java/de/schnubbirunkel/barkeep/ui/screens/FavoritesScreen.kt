@file:OptIn(ExperimentalMaterial3Api::class)

package de.schnubbirunkel.barkeep.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.data.UserData
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.EmptyHint
import de.schnubbirunkel.barkeep.ui.RecipeRow
import de.schnubbirunkel.barkeep.ui.Routes
import de.schnubbirunkel.barkeep.ui.appLang

private const val MINE = "\u0000mine"

@Composable
fun FavoritesScreen(vm: AppViewModel, nav: NavController) {
    val lang = appLang()
    val user by vm.user.collectAsState()
    var folder by rememberSaveable { mutableStateOf(UserData.DEFAULT_FOLDER) }
    var dialog by remember { mutableStateOf<String?>(null) } // "new" | "rename"
    var confirmDelete by remember { mutableStateOf(false) }
    val defaultName = stringResource(R.string.default_folder)
    if (folder != MINE && folder !in user.folders) folder = UserData.DEFAULT_FOLDER

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(modifier = Modifier.padding(bottom = de.schnubbirunkel.barkeep.ui.LocalBottomBarSpace.current), onClick = { nav.navigate(Routes.edit()) }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_recipe)) })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { dialog = "new" }) { Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.new_folder)) }
                de.schnubbirunkel.barkeep.ui.SettingsButton(nav)
                if (folder != MINE && folder != UserData.DEFAULT_FOLDER) {
                    IconButton(onClick = { dialog = "rename" }) { Icon(Icons.Filled.DriveFileRenameOutline, stringResource(R.string.rename)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            }
            ChipRow {
                FilterChip(folder == MINE, { folder = MINE }, { Text(stringResource(R.string.my_recipes) + " · " + user.custom.size) })
                user.folders.forEach { (f, ids) ->
                    FilterChip(folder == f, { folder = f }, { Text((if (f == UserData.DEFAULT_FOLDER) defaultName else f) + " · " + ids.size) })
                }
            }
            val recipes = if (folder == MINE) user.custom else user.folders[folder].orEmpty().mapNotNull { vm.recipe(it) }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp + de.schnubbirunkel.barkeep.ui.LocalBottomBarSpace.current)) {
                if (recipes.isEmpty()) item { EmptyHint(stringResource(R.string.empty_folder)) }
                items(recipes, key = { it.id }) { r -> RecipeRow(r, vm.catalog, lang, { nav.navigate(Routes.recipe(r.id)) }, Modifier.animateItem(), "fav") }
            }
        }
    }

    dialog?.let { mode ->
        var name by remember { mutableStateOf(if (mode == "rename") folder else "") }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (mode == "new") R.string.new_folder else R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (mode == "new") { vm.createFolder(name); folder = name.trim() } else { vm.renameFolder(folder, name); folder = name.trim() }
                    dialog = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(folder) },
        confirmButton = { TextButton(onClick = { vm.deleteFolder(folder); folder = UserData.DEFAULT_FOLDER; confirmDelete = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
