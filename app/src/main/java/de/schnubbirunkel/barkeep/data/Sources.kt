package de.schnubbirunkel.barkeep.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloadable recipe collections. Nothing of this is shipped in the APK or the repository –
 * the data is fetched on the device when the user enables a source.
 */
object SourceIds {
    const val COCKTAILDB = "cocktaildb"
    const val COMMUNITY = "community"
}

class DownloadedSources(context: Context, private val catalog: Catalog) {
    private val dir = File(context.filesDir, "sources").apply { mkdirs() }
    private val serializer = ListSerializer(Recipe.serializer())

    fun file(id: String) = File(dir, "$id.json")
    fun isDownloaded(id: String) = file(id).exists()

    fun load(id: String): List<Recipe> = runCatching {
        if (!file(id).exists()) emptyList() else AppJson.decodeFromString(serializer, file(id).readText())
    }.getOrDefault(emptyList())

    fun delete(id: String) { file(id).delete() }

    private fun save(id: String, list: List<Recipe>) {
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(AppJson.encodeToString(serializer, list))
        tmp.renameTo(file(id))
    }

    /** Downloads the whole TheCocktailDB catalogue by first letter (a–z, 0–9). */
    suspend fun downloadCocktailDb(key: String, progress: (Float) -> Unit): Int = withContext(Dispatchers.IO) {
        val db = CocktailDb { key }
        val letters = ('a'..'z') + ('0'..'9')
        val out = LinkedHashMap<String, Recipe>()
        letters.forEachIndexed { i, c ->
            runCatching { db.byLetter(c) }.getOrNull()?.forEach { out[it.id] = Enrich.apply(it, catalog) }
            progress((i + 1f) / letters.size)
        }
        if (out.isEmpty()) throw IllegalStateException("No data")
        save(SourceIds.COCKTAILDB, out.values.toList())
        out.size
    }

    /**
     * Community dataset "erwanlc/cocktails_recipe_no_brand" on Hugging Face, read through the
     * public datasets-server API in pages of 100 rows. For private use only.
     */
    suspend fun downloadCommunity(progress: (Float) -> Unit): Int = withContext(Dispatchers.IO) {
        // Preferred: the whole dataset as one CSV file – a single request, no rate limit trouble.
        val out = try {
            downloadCommunityCsv(progress)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            downloadCommunityPaged(progress)
        }
        if (out.isEmpty()) throw IllegalStateException("No data")
        save(SourceIds.COMMUNITY, out)
        out.size
    }

    private fun downloadCommunityCsv(progress: (Float) -> Unit): List<Recipe> {
        val url = "https://huggingface.co/datasets/erwanlc/cocktails_recipe_no_brand/resolve/main/train.csv"
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Barkeep-Android")
        }
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: 20_000_000L
            val counting = CountingStream(c.inputStream)
            val reader = counting.bufferedReader(Charsets.UTF_8)
            val out = ArrayList<Recipe>()
            var header: List<String>? = null
            var index = 0
            var lastReport = 0L
            Csv.records(reader) { fields ->
                val h = header
                if (h == null) { header = fields.map { it.trim().lowercase() }; return@records }
                val row = h.indices.associate { i -> h[i] to fields.getOrElse(i) { "" } }
                parseCommunityMap(row, index++)?.let { out += Enrich.apply(it, catalog) }
                if (counting.count - lastReport > 200_000) {
                    lastReport = counting.count
                    progress((counting.count.toFloat() / total).coerceAtMost(0.99f))
                }
            }
            progress(1f)
            return out
        } finally { c.disconnect() }
    }

    /** Fallback: datasets-server pages, slowly and with back-off (it answers 429 when hit too fast). */
    private fun downloadCommunityPaged(progress: (Float) -> Unit): List<Recipe> {
        val base = "https://datasets-server.huggingface.co/rows?dataset=erwanlc%2Fcocktails_recipe_no_brand&config=default&split=train"
        val out = ArrayList<Recipe>()
        var offset = 0
        var total = Int.MAX_VALUE
        var failures = 0
        while (offset < total) {
            val page = try {
                AppJson.parseToJsonElement(get("$base&offset=$offset&length=100")).jsonObject
            } catch (e: Exception) {
                if (++failures > 8) throw e
                Thread.sleep(minOf(60_000L, 2_000L shl failures))
                continue
            }
            failures = 0
            total = page["num_rows_total"]?.jsonPrimitive?.int ?: 0
            val rows = page["rows"] as? JsonArray ?: break
            if (rows.isEmpty()) break
            rows.forEachIndexed { i, el ->
                val row = (el as? JsonObject)?.get("row") as? JsonObject ?: return@forEachIndexed
                parseCommunity(row, offset + i)?.let { out += Enrich.apply(it, catalog) }
            }
            offset += rows.size
            progress((offset.toFloat() / total).coerceAtMost(1f))
            Thread.sleep(700)
        }
        return out
    }

    private fun parseCommunityMap(row: Map<String, String>, index: Int): Recipe? {
        val o = kotlinx.serialization.json.buildJsonObject {
            row.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
        }
        return parseCommunity(o, index)
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun parseCommunity(row: JsonObject, index: Int): Recipe? {
        val title = row.s("title").ifEmpty { return null }
        val ings = parsePyPairs(row.s("raw_ingredients")).map { (measure, name) ->
            CocktailDb.parseMeasure(name.replaceFirstChar { it.uppercase() }, measure)
        }
        if (ings.isEmpty()) return null
        val steps = CocktailDb.splitSteps(row.s("recipe"))
        val garnish = row.s("garnish").takeIf { it.isNotEmpty() && it.lowercase() != "none" }
        return Recipe(
            id = "cm:$index", name = title, glass = CocktailDb.mapGlass(row.s("glass")),
            ingredients = ings, steps = mapOf("en" to steps),
            garnish = garnish?.let { LocalizedText(en = it, de = it) },
            source = SourceIds.COMMUNITY,
        )
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("User-Agent", "Barkeep-Android")
        }
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private class CountingStream(private val inner: java.io.InputStream) : java.io.InputStream() {
        var count = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) count += it }
        override fun close() = inner.close()
    }

    companion object {
        private val pairRegex = Regex("""\[\s*(['"])(.*?)\1\s*,\s*(['"])(.*?)\3\s*]""")

        /** Parses python-style lists like [['4.5 cl', 'gin'], ['2 dash', "peychaud's"]]. */
        fun parsePyPairs(s: String): List<Pair<String, String>> =
            pairRegex.findAll(s).map { it.groupValues[2] to it.groupValues[4] }.filter { it.second.isNotBlank() }.toList()
    }
}

/** Derives type, styles, flavours and a colour for recipes from outside sources. */
object Enrich {
    private val colorHints = listOf(
        "blue_curacao" to "#2DA8D8", "campari" to "#C2262E", "aperol" to "#F26A21", "grenadine" to "#E0485A",
        "cranberry_juice" to "#C8324A", "tomato_juice" to "#B8262A", "coffee_liqueur" to "#3B2418", "espresso" to "#3B2418",
        "cola" to "#4A2614", "creme_de_menthe" to "#A9DDA0", "green_chartreuse" to "#B9D36A", "cream" to "#E6D3B5",
        "irish_cream" to "#D9C3A5", "milk" to "#EEE6D6", "cream_of_coconut" to "#F4EBD0", "red_wine" to "#7A1E2E",
        "creme_de_cassis" to "#8E1E3A", "raspberry_syrup" to "#E06080", "orange_juice" to "#F6B03A",
        "pineapple_juice" to "#F2D46A", "sweet_vermouth" to "#9C3D22", "dark_rum" to "#9A5A2A",
        "bourbon" to "#C77A2E", "rye" to "#C77A2E", "scotch" to "#D49A4A", "cognac" to "#C27A3A",
        "lime_juice" to "#E2EEB8", "lemon_juice" to "#F2EDB8",
    )

    fun apply(r: Recipe, catalog: Catalog): Recipe {
        val ids = r.ingredients.map { catalog.idOf(it) }.toSet()
        val cats = ids.map { catalog.category(it) }.toSet()
        val alcoholic = cats.any { it == "spirit" || it == "liqueur" } || ids.any { it in setOf("sparkling_wine", "champagne", "prosecco", "red_wine", "white_wine", "beer", "dry_vermouth", "sweet_vermouth", "lillet", "port", "dry_sherry") } ||
            r.ingredients.any { val n = (it.name ?: "").lowercase(); listOf("rum", "gin", "vodka", "whisk", "tequila", "liqueur", "brandy", "schnapps", "vermouth", "wine", "bitters").any { w -> w in n } }
        val type = when {
            r.type == "shot" || r.type == "punch" || r.type == "smoothie" -> r.type
            !alcoholic && ids.any { it in setOf("banana", "yogurt", "milk", "strawberries", "mango", "spinach") } -> "smoothie"
            !alcoholic -> "mocktail"
            r.glass == "shot" -> "shot"
            r.glass == "punch_bowl" -> "punch"
            else -> "cocktail"
        }
        val citrus = ids.any { it in setOf("lime_juice", "lemon_juice", "grapefruit_juice") }
        val sweet = ids.any { catalog.category(it) == "syrup" } || cats.contains("liqueur")
        val styles = LinkedHashSet(r.styles)
        val flavors = LinkedHashSet(r.flavors)
        if (citrus && sweet) { styles += "sour"; flavors += "sour" }
        if (ids.any { it in setOf("soda", "tonic", "ginger_beer", "ginger_ale", "cola", "lemon_soda", "grapefruit_soda") }) { styles += "highball"; flavors += "refreshing" }
        if (ids.any { it in setOf("prosecco", "champagne", "sparkling_wine") }) { styles += "sparkling"; flavors += "sparkling" }
        if (ids.any { it in setOf("cream_of_coconut", "orgeat", "falernum", "allspice_dram") } && ids.any { catalog.get(it)?.en?.contains("rum", true) == true }) styles += "tiki"
        if (ids.any { it in setOf("campari", "aperol", "fernet", "amaro_nonino") }) { styles += "bitter"; flavors += "bitter" }
        if (ids.any { it in setOf("cream", "irish_cream", "milk", "egg_white", "whole_egg", "vanilla_ice_cream", "cream_of_coconut") }) flavors += "creamy"
        if (ids.any { it in setOf("coffee_liqueur", "espresso", "coffee") }) { styles += "coffee"; flavors += "coffee" }
        if (ids.any { it in setOf("mint") }) flavors += "minty"
        if (ids.any { it in setOf("islay_scotch", "mezcal") }) { styles += "smoky"; flavors += "smoky" }
        if (ids.any { it in setOf("green_chartreuse", "yellow_chartreuse", "benedictine", "absinthe") }) flavors += "herbal"
        if (ids.any { it in setOf("elderflower_liqueur", "elderflower_syrup", "creme_de_violette") }) flavors += "floral"
        if (ids.any { catalog.category(it) == "juice" && it !in setOf("lime_juice", "lemon_juice") } || cats.contains("fruit")) flavors += "fruity"
        if (!citrus && ids.none { catalog.category(it) in setOf("juice", "mixer", "dairy", "fruit") } && alcoholic) { styles += "stirred"; flavors += "strong" }
        if (sweet) flavors += "sweet"
        val color = colorHints.firstOrNull { it.first in ids }?.second ?: r.color
        return r.copy(type = type, styles = styles.toList(), flavors = flavors.toList(), color = color)
    }
}

/** Minimal streaming CSV reader (RFC 4180: quotes, escaped quotes, newlines inside quotes). */
object Csv {
    fun records(reader: java.io.Reader, onRecord: (List<String>) -> Unit) {
        val fields = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var c = reader.read()
        var any = false
        while (c != -1) {
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    val n = reader.read()
                    if (n == '"'.code) sb.append('"') else { inQuotes = false; c = n; continue }
                } else sb.append(ch)
            } else when (ch) {
                '"' -> { inQuotes = true; any = true }
                ',' -> { fields += sb.toString(); sb.setLength(0); any = true }
                '\r' -> {}
                '\n' -> {
                    if (any || sb.isNotEmpty()) { fields += sb.toString(); onRecord(ArrayList(fields)) }
                    fields.clear(); sb.setLength(0); any = false
                }
                else -> { sb.append(ch); any = true }
            }
            c = reader.read()
        }
        if (any || sb.isNotEmpty()) { fields += sb.toString(); onRecord(fields) }
    }
}
