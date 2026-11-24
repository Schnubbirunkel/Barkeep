@file:OptIn(ExperimentalMaterial3Api::class)

package de.schnubbirunkel.barkeep.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import de.schnubbirunkel.barkeep.BuildConfig
import de.schnubbirunkel.barkeep.R
import de.schnubbirunkel.barkeep.ai.VisionClient
import de.schnubbirunkel.barkeep.data.SourceIds
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.LocalSnackbar
import de.schnubbirunkel.barkeep.ui.SectionTitle
import de.schnubbirunkel.barkeep.ui.SnackOnce
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val user by vm.user.collectAsState()
    val downloading by vm.downloading.collectAsState()
    val dlMsg by vm.downloadMessage.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val s = user.settings
    val importOk = stringResource(R.string.import_ok)
    val importFail = stringResource(R.string.import_fail)

    val msgText = dlMsg?.let { m ->
        if (m.startsWith("OK:")) stringResource(R.string.source_done, m.removePrefix("OK:").toIntOrNull() ?: 0)
        else stringResource(R.string.source_failed, m.removePrefix("ERR:"))
    }
    SnackOnce(msgText) { vm.consumeDownloadMessage() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionTitle(stringResource(R.string.settings_language))
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            ChipRow {
                FilterChip(current.isEmpty(), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }, { Text(stringResource(R.string.lang_system)) })
                FilterChip(current.startsWith("de"), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("de")) }, { Text("Deutsch") })
                FilterChip(current.startsWith("en"), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }, { Text("English") })
            }
            SectionTitle(stringResource(R.string.settings_units))
            ChipRow { listOf("ml", "cl", "oz").forEach { u -> FilterChip(s.unitSystem == u, { vm.updateSettings { it.copy(unitSystem = u) } }, { Text(u) }) } }
            SwitchRow(stringResource(R.string.settings_beginner), stringResource(R.string.settings_beginner_sub), s.beginnerMode) { v -> vm.updateSettings { it.copy(beginnerMode = v) } }
            SwitchRow(stringResource(R.string.settings_alcoholic), stringResource(R.string.settings_alcoholic_sub), s.showAlcoholic) { v -> vm.updateSettings { it.copy(showAlcoholic = v) } }

            SectionTitle(stringResource(R.string.sources_title))
            SwitchRow(stringResource(R.string.source_builtin), stringResource(R.string.source_builtin_sub, vm.localRecipes.size), true, enabled = false) {}
            SourceRow(vm, SourceIds.COCKTAILDB, stringResource(R.string.source_cdb), stringResource(R.string.source_cdb_sub), SourceIds.COCKTAILDB in s.sources, downloading[SourceIds.COCKTAILDB])
            SourceRow(vm, SourceIds.COMMUNITY, stringResource(R.string.source_community), stringResource(R.string.source_community_sub), SourceIds.COMMUNITY in s.sources, downloading[SourceIds.COMMUNITY])
            SwitchRow(stringResource(R.string.settings_online), stringResource(R.string.settings_online_sub), s.useOnline) { v -> vm.updateSettings { it.copy(useOnline = v) } }
            Field(stringResource(R.string.settings_cdb_key), s.cocktailDbKey, stringResource(R.string.settings_cdb_key_sub)) { v -> vm.updateSettings { it.copy(cocktailDbKey = v.trim()) } }

            SectionTitle(stringResource(R.string.settings_ai))
            Text(stringResource(R.string.settings_ai_provider), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp))
            ChipRow {
                listOf("gemini" to "Google Gemini", "anthropic" to "Claude", "openai" to "OpenAI").forEach { (id, label) ->
                    FilterChip(s.aiProvider == id, { vm.updateSettings { it.copy(aiProvider = id) } }, { Text(label) })
                }
            }
            Field(stringResource(R.string.settings_ai_key), s.aiKey, stringResource(R.string.settings_ai_key_sub), secret = true) { v -> vm.updateSettings { it.copy(aiKey = v.trim()) } }
            Field(stringResource(R.string.settings_ai_model), s.aiModel, VisionClient.defaultModel(s.aiProvider)) { v -> vm.updateSettings { it.copy(aiModel = v.trim()) } }

            SectionTitle(stringResource(R.string.settings_data))
            TextButton(onClick = {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, vm.exportData()) }, null))
            }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.settings_export)) }
            TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                val ok = text.isNotBlank() && vm.importData(text)
                scope.launch { snackbar.showSnackbar(if (ok) importOk else importFail) }
            }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.settings_import)) }

            SectionTitle(stringResource(R.string.settings_about))
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun SourceRow(vm: AppViewModel, id: String, title: String, sub: String, enabled: Boolean, progress: Float?) {
    Column {
        SwitchRow(title, sub, enabled || progress != null, enabled = progress == null) { v -> vm.setSource(id, v) }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            Text(stringResource(R.string.source_downloading, (progress * 100).toInt()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        } else if (vm.sourceCount(id) > 0) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.source_count, vm.sourceCount(id)), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.downloadSource(id) }) { Icon(Icons.Filled.Refresh, stringResource(R.string.source_update)) }
                IconButton(onClick = { vm.deleteSource(id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun Field(label: String, value: String, hint: String?, secret: Boolean = false, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text, onValueChange = { text = it; onSave(it) },
        label = { Text(label) }, supportingText = hint?.let { { Text(it) } },
        singleLine = true, visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
