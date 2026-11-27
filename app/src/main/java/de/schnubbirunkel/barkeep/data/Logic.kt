package de.schnubbirunkel.barkeep.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Amount formatting, unit conversion and scaling. */
object Units {
    const val ML_PER_OZ = 29.5735

    /** Returns (amountText, unitCode) for display in the chosen unit system. */
    fun convert(amount: Double, unit: String, system: String): Pair<Double, String> = when {
        unit == "ml" && system == "oz" -> amount / ML_PER_OZ to "oz"
        unit == "ml" && system == "cl" && amount >= 5 -> amount / 10.0 to "cl"
        else -> amount to unit
    }

    fun format(amount: Double, unit: String): String = when (unit) {
        "oz" -> fraction(amount, 4)
        "cl" -> trimNum((amount * 2).roundToInt() / 2.0)
        "ml" -> if (amount >= 100) ((amount / 5).roundToInt() * 5).toString() else trimNum((amount * 2).roundToInt() / 2.0)
        "g" -> if (amount >= 50) ((amount / 5).roundToInt() * 5).toString() else trimNum(amount.roundToInt().toDouble())
        "dash", "pinch", "pc", "slice", "leaf", "sprig", "scoop" -> fraction(amount, 2)
        else -> fraction(amount, 4)
    }

    private fun trimNum(d: Double): String =
        if (abs(d - d.roundToInt()) < 0.001) d.roundToInt().toString() else ((d * 10).roundToInt() / 10.0).toString()

    /** Nice kitchen fractions: 1 ½, ¾ … */
    fun fraction(d: Double, denom: Int): String {
        if (d <= 0) return "0"
        val total = (d * denom).roundToInt().coerceAtLeast(1)
        val whole = total / denom
        val rem = total % denom
        val f = when (denom) {
            4 -> when (rem) { 1 -> "¼"; 2 -> "½"; 3 -> "¾"; else -> "" }
            2 -> if (rem == 1) "½" else ""
            else -> ""
        }
        return when {
            whole == 0 && f.isNotEmpty() -> f
            f.isEmpty() -> whole.toString()
            else -> "$whole $f"
        }
    }

    /** Liquid volume of the recipe in ml (for "scale to volume"). */
    fun liquidVolumeMl(r: Recipe): Double = r.ingredients.sumOf { i ->
        val a = i.amount ?: 0.0
        when (i.unit) {
            "ml" -> a
            "tsp" -> a * 5
            "bsp" -> a * 5
            "tbsp" -> a * 15
            "dash" -> a * 0.8
            else -> 0.0
        }
    }

    fun scale(r: Recipe, factor: Double): Recipe =
        if (factor == 1.0) r else r.copy(ingredients = r.ingredients.map { it.copy(amount = it.amount?.times(factor)) })
}

/** "What can I make?" matching against the user's bar. */
data class MatchResult(
    val recipe: Recipe,
    val missing: List<String>,
    /** missing id -> substitute id the user has */
    val swaps: Map<String, String>,
) {
    val missingCount get() = missing.size
    val missingAfterSwaps get() = missing.count { it !in swaps }
}

object Matcher {
    fun requiredIds(r: Recipe, catalog: Catalog): List<String> =
        r.ingredients.map { catalog.idOf(it) }.filter { it !in Catalog.PANTRY }.distinct()

    fun match(r: Recipe, inventory: Set<String>, catalog: Catalog): MatchResult {
        val missing = requiredIds(r, catalog).filter { !has(it, inventory) }
        val swaps = buildMap {
            missing.forEach { m ->
                catalog.substitutes(m).firstOrNull { it.key in inventory }?.let { put(m, it.key) }
            }
        }
        return MatchResult(r, missing, swaps)
    }

    private fun has(id: String, inv: Set<String>): Boolean {
        if (id in inv) return true
        // fresh fruit implies juice and vice versa
        val alt = when (id) {
            "lime_juice" -> "lime"; "lime" -> "lime_juice"
            "lemon_juice" -> "lemon"; "lemon" -> "lemon_juice"
            "orange_juice" -> "orange"
            "sugar" -> "sugar_cube"; "sugar_cube" -> "sugar"
            "champagne" -> "sparkling_wine"; "sparkling_wine" -> "prosecco"
            "honey_syrup" -> "honey"; "simple_syrup" -> "sugar"
            else -> null
        }
        return alt != null && alt in inv
    }

    fun all(recipes: List<Recipe>, inventory: Set<String>, catalog: Catalog): List<MatchResult> =
        recipes.map { match(it, inventory, catalog) }
            .sortedWith(compareBy<MatchResult> { it.missingCount }.thenBy { it.missingAfterSwaps }.thenBy { it.recipe.name })
}

object Discovery {
    /** Similar drinks via shared ingredients, styles and flavours. */
    fun similar(r: Recipe, all: List<Recipe>, catalog: Catalog, n: Int = 8): List<Recipe> {
        val a = Matcher.requiredIds(r, catalog).toSet()
        return all.asSequence().filter { it.id != r.id }.map { o ->
            val b = Matcher.requiredIds(o, catalog).toSet()
            val inter = (a intersect b).size.toDouble()
            val union = (a union b).size.coerceAtLeast(1)
            val score = inter / union * 3 +
                (r.styles intersect o.styles.toSet()).size * 0.4 +
                (r.flavors intersect o.flavors.toSet()).size * 0.3 +
                (if (r.type == o.type) 0.3 else 0.0)
            o to score
        }.sortedByDescending { it.second }.take(n).map { it.first }.toList()
    }

    /** Deterministic "drink of the day". */
    fun daily(all: List<Recipe>, date: LocalDate = LocalDate.now(), offset: Int = 0): Recipe? {
        if (all.isEmpty()) return null
        val seed = date.toEpochDay().toInt() * 31 + offset * 7919
        return all[seed.mod(all.size)]
    }

    fun currentSeason(date: LocalDate = LocalDate.now()): List<String> {
        val m = date.monthValue
        val base = when (m) {
            12, 1, 2 -> "winter"; 3, 4, 5 -> "spring"; 6, 7, 8 -> "summer"; else -> "autumn"
        }
        return if (m == 12 || (m == 11 && date.dayOfMonth > 20)) listOf("christmas", base) else listOf(base)
    }

    val POPULAR = listOf(
        "aperol_spritz", "mojito", "margarita", "espresso_martini", "negroni", "old_fashioned", "gin_tonic",
        "hugo", "pina_colada", "moscow_mule", "whiskey_sour", "pornstar_martini", "daiquiri", "cosmopolitan",
        "mai_tai", "paloma", "french_75", "white_russian", "caipirinha", "penicillin",
    )

    /** Food pairing suggestions built from flavour profile and base spirit. */
    fun pairings(r: Recipe, catalog: Catalog, lang: String): List<String> {
        val de = lang == "de"
        val ids = Matcher.requiredIds(r, catalog).toSet()
        val out = LinkedHashSet<String>()
        fun add(en: String, d: String) { out += if (de) d else en }
        val f = r.flavors.toSet()
        val st = r.styles.toSet()
        if ("bitter" in f || "spritz" in st) { add("Antipasti & olives", "Antipasti & Oliven"); add("Salty snacks & chips", "Salzige Snacks & Chips") }
        if ("sour" in f && ("agave" in st || ids.any { it in setOf("tequila", "mezcal", "reposado") })) { add("Tacos & guacamole", "Tacos & Guacamole"); add("Ceviche", "Ceviche") }
        if ("sour" in f) add("Seafood & fried fish", "Meeresfrüchte & Backfisch")
        if ("smoky" in f || ids.any { it in setOf("bourbon", "rye", "scotch", "islay_scotch") }) { add("BBQ & smoked meats", "BBQ & Geräuchertes"); add("Aged cheese", "Gereifter Käse") }
        if ("tiki" in st || "fruity" in f) { add("Spicy Asian dishes", "Scharfe asiatische Gerichte"); add("Grilled pork & pineapple", "Gegrilltes Schwein mit Ananas") }
        if ("creamy" in f || "dessert" in st) { add("Desserts & cheesecake", "Desserts & Käsekuchen"); add("Dark chocolate", "Zartbitterschokolade") }
        if ("coffee" in f) add("Tiramisu & brownies", "Tiramisu & Brownies")
        if ("herbal" in f || "floral" in f) { add("Fresh salads & goat cheese", "Frische Salate & Ziegenkäse"); add("Sushi", "Sushi") }
        if ("sparkling" in st || "sparkling" in f) { add("Oysters & canapés", "Austern & Kanapees"); add("Fried snacks", "Frittiertes Fingerfood") }
        if ("savory" in f) { add("Brunch: eggs & bacon", "Brunch: Eier & Speck") }
        if ("warm" in f || "hot" in st) { add("Christmas cookies & gingerbread", "Plätzchen & Lebkuchen"); add("Roasted chestnuts", "Geröstete Maronen") }
        if ("refreshing" in f && out.size < 3) add("Grilled vegetables & light mezze", "Grillgemüse & leichte Mezze")
        if (r.type == "smoothie") { out.clear(); add("Granola & breakfast bowls", "Granola & Frühstücksbowls"); add("Pancakes", "Pancakes") }
        if (out.isEmpty()) { add("Cheese board", "Käseplatte"); add("Nuts & salty snacks", "Nüsse & salzige Snacks") }
        return out.take(4)
    }
}
