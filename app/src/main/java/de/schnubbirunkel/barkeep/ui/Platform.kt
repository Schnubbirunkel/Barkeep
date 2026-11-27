package de.schnubbirunkel.barkeep.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/*
 * Platform layer (Android). Everything the shared UI needs from the operating system goes through
 * these functions; the web build (web/) has its own Platform.kt with the same signatures.
 */

fun nowMillis(): Long = System.currentTimeMillis()

/** UI language of the app: "de" or "en". */
@Composable
fun appLang(): String {
    val l = LocalConfiguration.current.locales[0]?.language ?: "en"
    return if (l == "de") "de" else "en"
}

/** Language chosen in the settings: "" = system, "de", "en". */
fun appLanguageSetting(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
}

/** Looks up a string resource outside of composition (for labels computed in callbacks). */
@Composable
fun rememberStrings(): (Int) -> String {
    val ctx = LocalContext.current
    return remember(ctx) { { id: Int -> ctx.getString(id) } }
}

/** Opens the system share sheet with plain text. */
@Composable
fun rememberShareText(): (String) -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) {
        { text: String ->
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))
        }
    }
}

/** Hands the JSON backup to the user (share sheet on Android, file download on the web). */
@Composable
fun rememberExportData(): (String) -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) {
        { json: String ->
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, json) }, null))
        }
    }
}

/** Reads a JSON backup (from the clipboard on Android, from a chosen file on the web). */
@Composable
fun rememberImportData(): ((String) -> Unit) -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) {
        { onText: (String) -> Unit ->
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            onText(cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty())
        }
    }
}

/** Starts the barcode scanner; returns a function that opens it. */
@Composable
fun rememberBarcodeScanner(onCode: (String) -> Unit, onError: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    return {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let(onCode) }
            .addOnFailureListener { onError() }
    }
}

// ------------------------------------------------------------------ photos for AI recognition
typealias Photo = Bitmap

fun Photo.toImageBitmap(): ImageBitmap = asImageBitmap()

suspend fun Photo.toJpegBase64(maxSide: Int = 1024): String = withContext(Dispatchers.Default) {
    val scale = maxSide.toFloat() / maxOf(width, height)
    val b = if (scale < 1f) Bitmap.createScaledBitmap(this@toJpegBase64, (width * scale).toInt(), (height * scale).toInt(), true) else this@toJpegBase64
    val out = ByteArrayOutputStream()
    b.compress(Bitmap.CompressFormat.JPEG, 82, out)
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

class PhotoPicker(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

@Composable
fun rememberPhotoPicker(onPhoto: (Photo) -> Unit): PhotoPicker {
    val ctx = LocalContext.current
    val photoFile = remember { File(ctx.cacheDir, "scan.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) loadBitmap(ctx, photoUri)?.let(onPhoto) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) loadBitmap(ctx, uri)?.let(onPhoto) }
    return PhotoPicker(
        takePhoto = { camera.launch(photoUri) },
        pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

private fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()
