package de.schnubbirunkel.barkeep.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.schnubbirunkel.barkeep.FONT_FILES
import de.schnubbirunkel.barkeep.STRINGS_DE
import de.schnubbirunkel.barkeep.STRINGS_EN
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Data the shared code reads synchronously on Android; loaded once before the UI starts. */
object WebEnv {
    lateinit var recipesJson: String
    lateinit var ingredientsJson: String
    val fonts = HashMap<Int, ByteArray>()
    /** Downloaded recipe collections (JSON), persisted in IndexedDB. */
    val sources = HashMap<String, String>()
    val sourceIds = listOf("cocktaildb", "community")

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun preload() = coroutineScope {
        jsRequestPersistence()
        val recipes = async { text("recipes.json") }
        val ingredients = async { text("ingredients.json") }
        val fontJobs = FONT_FILES.mapIndexed { i, f -> async { i to Base64.decode(jsString(jsFetchBase64(f).await<JsAny?>())!!) } }
        val sourceJobs = sourceIds.map { id -> async { id to jsString(jsIdbGet("source:$id").await<JsAny?>()) } }
        recipesJson = recipes.await()
        ingredientsJson = ingredients.await()
        fontJobs.awaitAll().forEach { (i, b) -> fonts[i] = b }
        sourceJobs.awaitAll().forEach { (id, t) -> if (t != null) sources[id] = t }
    }

    private suspend fun text(url: String): String {
        val r = jsFetch("GET", url, null, "{}", 60000).await<JsAny?>()
        check(jsResponseStatus(r) in 200..299) { "HTTP ${jsResponseStatus(r)} for $url" }
        return jsResponseText(r)
    }
}

/** App language. "" = follow the browser. */
object WebLang {
    var setting by mutableStateOf(jsLocalGet("barkeep:lang") ?: "")
        private set

    val effective: String
        get() = setting.ifEmpty { jsNavigatorLanguage() }.lowercase().let { if (it.startsWith("de")) "de" else "en" }

    fun set(tag: String) {
        setting = tag
        jsLocalSet("barkeep:lang", tag)
        jsSetDocumentLang(effective)
    }
}

internal fun jsSetDocumentLang(lang: String) { js("document.documentElement.lang = lang") }

fun webString(id: Int, lang: String = WebLang.effective): String =
    (if (lang == "de") STRINGS_DE else STRINGS_EN).getOrElse(id) { "?" }

/** Android-style format strings: %1$s, %2$d, %s, %d, %%. */
fun formatAndroid(template: String, args: Array<out Any?>): String {
    var next = 0
    return Regex("""%(?:(\d+)\$)?([sdf%])""").replace(template) { m ->
        if (m.groupValues[2] == "%") return@replace "%"
        val idx = m.groupValues[1].toIntOrNull()?.minus(1) ?: next++
        args.getOrNull(idx)?.toString() ?: ""
    }
}
