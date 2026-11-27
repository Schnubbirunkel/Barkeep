package de.schnubbirunkel.barkeep.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.schnubbirunkel.barkeep.web.WebLang
import de.schnubbirunkel.barkeep.web.jsDownload
import de.schnubbirunkel.barkeep.web.jsNow
import de.schnubbirunkel.barkeep.web.jsPhotoLarge
import de.schnubbirunkel.barkeep.web.jsPhotoSmall
import de.schnubbirunkel.barkeep.web.jsPickPhoto
import de.schnubbirunkel.barkeep.web.jsPickTextFile
import de.schnubbirunkel.barkeep.web.jsScanBarcode
import de.schnubbirunkel.barkeep.web.jsShareText
import de.schnubbirunkel.barkeep.web.jsString
import de.schnubbirunkel.barkeep.web.webString
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * Platform layer (web). Same signatures as app/…/ui/Platform.kt.
 * Browser APIs that need a user gesture (file pickers, share) are started synchronously
 * inside the click handler and only awaited afterwards.
 */

fun nowMillis(): Long = jsNow().toLong()

/** Static in the browser: Compose/Wasm repaints the whole canvas for every animation frame. */
const val ANIMATED_BACKGROUND = false

@Composable
fun appLang(): String = WebLang.effective

fun appLanguageSetting(): String = WebLang.setting

fun setAppLanguage(tag: String) = WebLang.set(tag)

@Composable
fun rememberStrings(): (Int) -> String = { id -> webString(id) }

@Composable
fun rememberShareText(): (String) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) { { text: String -> val p = jsShareText(text); scope.launch { runCatching { p.await<JsAny?>() } } } }
}

@Composable
fun rememberExportData(): (String) -> Unit =
    remember { { json: String -> jsDownload("barkeep-backup.json", "application/json", json) } }

@Composable
fun rememberImportData(): ((String) -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { onText: (String) -> Unit ->
            val p = jsPickTextFile("application/json,.json,text/plain")
            scope.launch { jsString(runCatching { p.await<JsAny?>() }.getOrNull())?.let(onText) }
        }
    }
}

@Composable
fun rememberBarcodeScanner(onCode: (String) -> Unit, onError: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        val p = jsScanBarcode()
        scope.launch {
            runCatching { jsString(p.await<JsAny?>()).orEmpty() }
                .onSuccess { if (it.isNotEmpty()) onCode(it) }
                .onFailure { onError() }
        }
    }
}

// ------------------------------------------------------------------ photos for AI recognition
/** A picked photo as base64 JPEGs: [jpeg] for display (≤1600 px), [small] for the AI (≤1024 px). */
class Photo(val jpeg: String, val small: String) {
    @OptIn(ExperimentalEncodingApi::class)
    val image: ImageBitmap by lazy { Image.makeFromEncoded(Base64.decode(jpeg)).toComposeImageBitmap() }
}

fun Photo.toImageBitmap(): ImageBitmap = image

@Suppress("UNUSED_PARAMETER")
suspend fun Photo.toJpegBase64(maxSide: Int = 1024): String = small

class PhotoPicker(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

@Composable
fun rememberPhotoPicker(onPhoto: (Photo) -> Unit): PhotoPicker {
    val scope = rememberCoroutineScope()
    fun pick(camera: Boolean) {
        val p = jsPickPhoto(camera)
        scope.launch {
            val r = runCatching { p.await<JsAny?>() }.getOrNull() ?: return@launch
            onPhoto(Photo(jsPhotoLarge(r), jsPhotoSmall(r)))
        }
    }
    return PhotoPicker(takePhoto = { pick(true) }, pickPhoto = { pick(false) })
}
