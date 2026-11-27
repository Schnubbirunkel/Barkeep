package de.schnubbirunkel.barkeep.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.EmptyHint

@Composable
fun ShoppingScreen(vm: AppViewModel, nav: androidx.navigation.NavController) {
    val share = de.schnubbirunkel.barkeep.ui.rememberShareText()
    val user by vm.user.collectAsState()
    var text by remember { mutableStateOf("") }
    val title = stringResource(R.string.shopping_share_title)
    Column(Modifier.statusBarsPadding().padding(bottom = de.schnubbirunkel.barkeep.ui.LocalBottomBarSpace.current)) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_shopping), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(enabled = user.shopping.isNotEmpty(), onClick = {
                val body = title + "\n\n" + user.shopping.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text }
                share(body)
            }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
            de.schnubbirunkel.barkeep.ui.SettingsButton(nav)
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text(stringResource(R.string.shopping_add)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addShopping(text); text = "" }),
            )
            IconButton(onClick = { vm.addShopping(text); text = "" }) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
        }
        if (user.shopping.isEmpty()) EmptyHint(stringResource(R.string.shopping_empty))
        LazyColumn(Modifier.weight(1f)) {
            items(user.shopping.sortedBy { it.done }, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().clickable { vm.toggleShopping(item.id) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.done, { vm.toggleShopping(item.id) })
                    Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { vm.removeShopping(item.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete)) }
                }
            }
        }
        if (user.shopping.any { it.done }) {
            Button(onClick = { vm.checkoutShopping() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.shopping_checkout))
            }
        }
    }
}
