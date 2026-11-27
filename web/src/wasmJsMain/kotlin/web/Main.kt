package de.schnubbirunkel.barkeep.web

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import de.schnubbirunkel.barkeep.ui.AppViewModel
import de.schnubbirunkel.barkeep.ui.BarkeepApp
import de.schnubbirunkel.barkeep.ui.BarkeepTheme
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        try {
            WebEnv.preload()
        } catch (e: Throwable) {
            jsShowStartError(e.message ?: e.toString())
            return@launch
        }
        jsSetDocumentLang(WebLang.effective)
        ComposeViewport(document.getElementById("app") ?: document.body!!) { WebApp() }
        jsHideSplash()
    }
}

@Composable
private fun WebApp() {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory()) }.build()
    }
    val vm = remember { AppViewModel(Application()) }
    val user by vm.user.collectAsState()
    BarkeepTheme(user.settings.theme, user.settings.accent) {
        BarkeepApp(vm)
    }
}

private fun jsHideSplash() { js("(() => { const s = document.getElementById('splash'); if (s) { s.classList.add('gone'); setTimeout(() => s.remove(), 400); } })()") }
private fun jsShowStartError(msg: String) { js("(() => { const s = document.getElementById('splash-msg'); if (s) s.textContent = 'Barkeep konnte nicht starten / could not start: ' + msg; })()") }
