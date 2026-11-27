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
            Csv.records({ reader.read() }) { fields ->
                val h = header
                if (h == null) { header = fields.map { it.trim().lowercase() }; return@records }
                val row = h.indices.associate { i -> h[i] to fields.getOrElse(i) { "" } }
                Community.parseMap(row, index++)?.let { out += Enrich.apply(it, catalog) }
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
                Community.parse(row, offset + i)?.let { out += Enrich.apply(it, catalog) }
            }
            offset += rows.size
            progress((offset.toFloat() / total).coerceAtMost(1f))
            Thread.sleep(700)
        }
        return out
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
        fun parsePyPairs(s: String) = Community.parsePyPairs(s)
    }
}
