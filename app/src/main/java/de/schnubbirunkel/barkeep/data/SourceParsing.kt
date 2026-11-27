package de.schnubbirunkel.barkeep.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Downloadable recipe collections. Nothing of this is shipped in the APK or the repository –
 * the data is fetched on the device when the user enables a source.
 */
object SourceIds {
    const val COCKTAILDB = "cocktaildb"
    const val COMMUNITY = "community"
}

/** Parsing of the community dataset "erwanlc/cocktails_recipe_no_brand" (CSV rows or datasets-server JSON). */
object Community {
    private val pairRegex = Regex("""\[\s*(['"])(.*?)\1\s*,\s*(['"])(.*?)\3\s*]""")

    /** Parses python-style lists like [['4.5 cl', 'gin'], ['2 dash', "peychaud's"]]. */
    fun parsePyPairs(s: String): List<Pair<String, String>> =
        pairRegex.findAll(s).map { it.groupValues[2] to it.groupValues[4] }.filter { it.second.isNotBlank() }.toList()

    fun parseMap(row: Map<String, String>, index: Int): Recipe? {
        val o = kotlinx.serialization.json.buildJsonObject {
            row.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
        }
        return parse(o, index)
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    fun parse(row: JsonObject, index: Int): Recipe? {
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
    /** [read] returns the next char code, or -1 at the end (like java.io.Reader.read). */
    fun records(read: () -> Int, onRecord: (List<String>) -> Unit) {
        val fields = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var c = read()
        var any = false
        while (c != -1) {
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    val n = read()
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
            c = read()
        }
        if (any || sb.isNotEmpty()) { fields += sb.toString(); onRecord(fields) }
    }
}
