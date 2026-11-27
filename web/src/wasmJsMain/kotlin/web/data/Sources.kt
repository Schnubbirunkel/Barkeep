package de.schnubbirunkel.barkeep.data

import de.schnubbirunkel.barkeep.web.WebEnv
import de.schnubbirunkel.barkeep.web.jsIdbDelete
import de.schnubbirunkel.barkeep.web.jsIdbPut
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Downloadable recipe collections, stored in IndexedDB (web counterpart of app/…/data/Sources.kt). */
class DownloadedSources(@Suppress("UNUSED_PARAMETER") context: Any?, private val catalog: Catalog) {
    private val serializer = ListSerializer(Recipe.serializer())
    private val scope = MainScope()

    fun isDownloaded(id: String) = WebEnv.sources[id] != null

    fun load(id: String): List<Recipe> = runCatching {
        WebEnv.sources[id]?.let { AppJson.decodeFromString(serializer, it) } ?: emptyList()
    }.getOrDefault(emptyList())

    fun delete(id: String) {
        WebEnv.sources.remove(id)
        scope.launch { jsIdbDelete("source:$id").await<JsAny?>() }
    }

    private suspend fun save(id: String, list: List<Recipe>) {
        val text = AppJson.encodeToString(serializer, list)
        WebEnv.sources[id] = text
        jsIdbPut("source:$id", text).await<JsAny?>()
    }

    suspend fun downloadCocktailDb(key: String, progress: (Float) -> Unit): Int {
        val db = CocktailDb { key }
        val letters = ('a'..'z') + ('0'..'9')
        val out = LinkedHashMap<String, Recipe>()
        letters.forEachIndexed { i, c ->
            runCatching { db.byLetter(c) }.getOrNull()?.forEach { out[it.id] = Enrich.apply(it, catalog) }
            progress((i + 1f) / letters.size)
        }
        if (out.isEmpty()) throw IllegalStateException("No data")
        save(SourceIds.COCKTAILDB, out.values.toList())
        return out.size
    }

    suspend fun downloadCommunity(progress: (Float) -> Unit): Int {
        val out = try {
            downloadCommunityCsv(progress)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            downloadCommunityPaged(progress)
        }
        if (out.isEmpty()) throw IllegalStateException("No data")
        save(SourceIds.COMMUNITY, out)
        return out.size
    }

    private suspend fun downloadCommunityCsv(progress: (Float) -> Unit): List<Recipe> {
        progress(0.05f)
        val res = Http.get("https://huggingface.co/datasets/erwanlc/cocktails_recipe_no_brand/resolve/main/train.csv", timeoutMs = 180000)
        if (!res.ok) throw IllegalStateException("HTTP ${res.code}")
        progress(0.6f)
        yield()
        val text = res.body
        var pos = 0
        val rows = ArrayList<List<String>>()
        Csv.records({ if (pos < text.length) text[pos++].code else -1 }) { rows += it }
        val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: return emptyList()
        val out = ArrayList<Recipe>()
        rows.drop(1).forEachIndexed { index, fields ->
            val row = header.indices.associate { i -> header[i] to fields.getOrElse(i) { "" } }
            Community.parseMap(row, index)?.let { out += Enrich.apply(it, catalog) }
            if (index % 500 == 0) { progress(0.6f + 0.39f * index / rows.size); yield() }
        }
        progress(1f)
        return out
    }

    private suspend fun downloadCommunityPaged(progress: (Float) -> Unit): List<Recipe> {
        val base = "https://datasets-server.huggingface.co/rows?dataset=erwanlc%2Fcocktails_recipe_no_brand&config=default&split=train"
        val out = ArrayList<Recipe>()
        var offset = 0
        var total = Int.MAX_VALUE
        var failures = 0
        while (offset < total) {
            val page = try {
                val res = Http.get("$base&offset=$offset&length=100", timeoutMs = 30000)
                if (!res.ok) throw IllegalStateException("HTTP ${res.code}")
                AppJson.parseToJsonElement(res.body).jsonObject
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (++failures > 8) throw e
                delay(minOf(60_000L, 2_000L shl failures))
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
            delay(700)
        }
        return out
    }
}
