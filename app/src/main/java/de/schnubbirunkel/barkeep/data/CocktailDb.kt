package de.schnubbirunkel.barkeep.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Optional online source: TheCocktailDB (https://www.thecocktaildb.com).
 * The test key "1" is for development/personal use; supporters get their own key.
 */
class CocktailDb(private val keyProvider: () -> String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val base get() = "https://www.thecocktaildb.com/api/json/v1/${keyProvider().ifBlank { "1" }}/"

    suspend fun search(query: String): List<Recipe> = fetch("search.php?s=" + enc(query))
    suspend fun byLetter(c: Char): List<Recipe> = fetch("search.php?f=$c")
    suspend fun random(): Recipe? = fetch("random.php").firstOrNull()
    suspend fun lookup(id: String): Recipe? = fetch("lookup.php?i=" + enc(id.removePrefix(PREFIX))).firstOrNull()

    /** Returns only ids + names + thumbnails, details need lookup(). */
    suspend fun byIngredient(ingredient: String): List<Recipe> = fetch("filter.php?i=" + enc(ingredient))

    private fun enc(s: String) = urlEncode(s)

    private suspend fun fetch(path: String): List<Recipe> {
        val res = Http.get(base + path, timeoutMs = 10000)
        if (!res.ok) throw IllegalStateException("HTTP ${res.code}")
        if (res.body.isBlank()) return emptyList()
        val root = json.parseToJsonElement(res.body).jsonObject
        val drinks = root["drinks"] as? JsonArray ?: return emptyList()
        return drinks.mapNotNull { (it as? JsonObject)?.let(::toRecipe) }
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun toRecipe(o: JsonObject): Recipe? {
        val id = o.str("idDrink") ?: return null
        val name = o.str("strDrink") ?: return null
        val ings = (1..15).mapNotNull { i ->
            val n = o.str("strIngredient$i") ?: return@mapNotNull null
            parseMeasure(n, o.str("strMeasure$i"))
        }
        val alcoholic = o.str("strAlcoholic")?.lowercase()
        val category = o.str("strCategory")?.lowercase().orEmpty()
        val type = when {
            alcoholic?.startsWith("non") == true -> if ("shake" in category || "smoothie" in category) "smoothie" else "mocktail"
            "shot" in category -> "shot"
            "punch" in category -> "punch"
            else -> "cocktail"
        }
        val stepsEn = splitSteps(o.str("strInstructions"))
        val stepsDe = splitSteps(o.str("strInstructionsDE"))
        val glass = mapGlass(o.str("strGlass"))
        val styles = buildList {
            o.str("strCategory")?.let { add(it.lowercase().replace(' ', '_').replace("/", "_")) }
            o.str("strIBA")?.let { add("classic") }
            o.str("strTags")?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.let { addAll(it) }
        }
        return Recipe(
            id = PREFIX + id, name = name, type = type, styles = styles, glass = glass,
            color = "#C8A86A", ingredients = ings,
            steps = buildMap { if (stepsEn.isNotEmpty()) put("en", stepsEn); if (stepsDe.isNotEmpty()) put("de", stepsDe) },
            imageUrl = o.str("strDrinkThumb"), source = RecipeSource.ONLINE,
        )
    }

    companion object {
        const val PREFIX = "cdb:"

        fun splitSteps(t: String?): List<String> {
            if (t.isNullOrBlank()) return emptyList()
            return t.replace("\r", "").split(Regex("(?<=[.!])\\s+|\\n+"))
                .map { it.trim() }.filter { it.length > 2 }
        }

        fun mapGlass(g: String?): String {
            val s = g?.lowercase() ?: return "rocks"
            return when {
                "coupe" in s || "coupette" in s || "champagne saucer" in s -> "coupe"
                "martini" in s || "cocktail glass" in s -> "martini"
                "highball" in s -> "highball"
                "collins" in s -> "collins"
                "flute" in s || "champagne" in s -> "flute"
                "copper" in s -> "copper_mug"
                "hurricane" in s -> "hurricane"
                "shot" in s || "cordial" in s -> "shot"
                "wine" in s -> "wine"
                "punch" in s || "pitcher" in s || "jar" in s -> "punch_bowl"
                "mug" in s || "irish coffee" in s || "coffee" in s -> "mug"
                "pint" in s || "beer" in s -> "pint"
                "tiki" in s -> "tiki"
                "nick" in s -> "nick_nora"
                "julep" in s -> "julep"
                else -> "rocks"
            }
        }

        private val unitMap = linkedMapOf(
            "oz" to ("ml" to 30.0), "ounce" to ("ml" to 30.0), "ounces" to ("ml" to 30.0),
            "cl" to ("ml" to 10.0), "ml" to ("ml" to 1.0), "dl" to ("ml" to 100.0), "l" to ("ml" to 1000.0),
            "shot" to ("ml" to 45.0), "shots" to ("ml" to 45.0), "jigger" to ("ml" to 45.0), "jiggers" to ("ml" to 45.0),
            "cup" to ("ml" to 240.0), "cups" to ("ml" to 240.0), "pint" to ("ml" to 470.0), "pints" to ("ml" to 470.0),
            "tsp" to ("tsp" to 1.0), "teaspoon" to ("tsp" to 1.0), "teaspoons" to ("tsp" to 1.0),
            "tblsp" to ("tbsp" to 1.0), "tbsp" to ("tbsp" to 1.0), "tablespoon" to ("tbsp" to 1.0), "tablespoons" to ("tbsp" to 1.0),
            "dash" to ("dash" to 1.0), "dashes" to ("dash" to 1.0), "splash" to ("splash" to 1.0),
            "pinch" to ("pinch" to 1.0), "slice" to ("slice" to 1.0), "slices" to ("slice" to 1.0),
            "sprig" to ("sprig" to 1.0), "sprigs" to ("sprig" to 1.0), "leaves" to ("leaf" to 1.0),
            "g" to ("g" to 1.0), "gr" to ("g" to 1.0),
        )

        /** Parses TheCocktailDB measures like "1 1/2 oz", "2 cl", "1-2 dashes", "Juice of 1/2". */
        fun parseMeasure(name: String, measure: String?): RecipeIngredient {
            val m = measure?.trim()?.lowercase().orEmpty()
            if (m.isEmpty()) return RecipeIngredient(name = name)
            val num = Regex("^(\\d+(?:[.,]\\d+)?)?\\s*(?:(\\d+)\\s*/\\s*(\\d+))?(?:\\s*-\\s*[\\d/ ]+)?\\s*(.*)$").find(m)
                ?: return RecipeIngredient(name = name, measure = measure)
            val (wholeS, nS, dS, restS) = num.destructured
            var amount = wholeS.replace(',', '.').toDoubleOrNull() ?: 0.0
            if (nS.isNotEmpty() && dS.isNotEmpty() && dS != "0") amount += nS.toDouble() / dS.toDouble()
            if (amount == 0.0) return RecipeIngredient(name = name, measure = measure)
            val word = restS.trim().split(' ').firstOrNull().orEmpty().trimEnd('.')
            val u = unitMap[word]
            return if (u != null) RecipeIngredient(name = name, amount = amount * u.second, unit = u.first)
            else if (word.isEmpty()) RecipeIngredient(name = name, amount = amount, unit = "pc")
            else RecipeIngredient(name = name, measure = measure)
        }
    }
}
