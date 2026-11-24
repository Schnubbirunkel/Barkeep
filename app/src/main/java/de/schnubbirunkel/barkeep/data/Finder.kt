package de.schnubbirunkel.barkeep.data

import kotlinx.serialization.Serializable

/** What the user is in the mood for (from the questionnaire or parsed by AI). */
@Serializable
data class Wish(
    /** "yes", "no" or "any" */
    val alcohol: String = "any",
    val flavors: Set<String> = emptySet(),
    /** spirit groups: gin, vodka, rum, agave, whiskey, brandy, wine */
    val spirits: Set<String> = emptySet(),
    /** 0 = any, 1 light, 2 medium, 3 strong */
    val strength: Int = 0,
    val occasions: Set<String> = emptySet(),
    /** -1 = don't care, 0 = only makeable now, 1 = at most one missing */
    val maxMissing: Int = -1,
)

data class Suggestion(val recipe: Recipe, val score: Double, val percent: Int, val reasons: List<String>, val missing: Int)

object Finder {
    val SPIRIT_GROUPS: Map<String, Set<String>> = mapOf(
        "gin" to setOf("gin", "sloe_gin", "genever"),
        "vodka" to setOf("vodka"),
        "rum" to setOf("white_rum", "dark_rum", "aged_rum", "overproof_rum", "cachaca", "rhum_agricole", "coconut_rum"),
        "agave" to setOf("tequila", "reposado", "mezcal"),
        "whiskey" to setOf("bourbon", "rye", "scotch", "islay_scotch", "irish_whiskey"),
        "brandy" to setOf("cognac", "brandy", "calvados", "pisco"),
        "wine" to setOf("sparkling_wine", "champagne", "prosecco", "red_wine", "white_wine", "lillet", "sweet_vermouth", "dry_vermouth", "blanc_vermouth", "port", "dry_sherry"),
    )

    val FLAVORS = listOf("sweet", "sour", "bitter", "fruity", "refreshing", "creamy", "herbal", "smoky", "spicy", "minty", "coffee", "floral")

    /** occasion -> (styles, flavours) that fit it */
    val OCCASIONS: Map<String, Pair<Set<String>, Set<String>>> = linkedMapOf(
        "aperitif" to (setOf("bitter", "spritz", "low_abv", "sparkling") to setOf("bitter", "dry")),
        "summer" to (setOf("highball", "frozen", "tiki", "spritz") to setOf("refreshing", "fruity", "fresh")),
        "party" to (setOf("party", "punch", "shot", "frozen", "tiki") to setOf("sweet", "fruity")),
        "dessert" to (setOf("dessert", "creamy", "coffee") to setOf("creamy", "sweet", "coffee")),
        "cozy" to (setOf("hot", "holiday", "stirred") to setOf("warm", "spicy", "strong")),
        "classic" to (setOf("classic") to emptySet()),
        "tiki" to (setOf("tiki") to setOf("fruity")),
    )

    fun strength(r: Recipe): Int = when {
        !r.isAlcoholic -> 0
        "low_abv" in r.styles || "spritz" in r.styles -> 1
        "stirred" in r.styles || "shot" == r.type -> 3
        "highball" in r.styles || "sparkling" in r.styles || r.type == "punch" -> 1
        "strong" in r.flavors -> 3
        else -> 2
    }

    fun suggest(wish: Wish, all: List<Recipe>, inventory: Set<String>, catalog: Catalog, lang: String, labels: (String) -> String, n: Int = 40): List<Suggestion> {
        val maxScore = wish.flavors.size * 2.0 + (if (wish.spirits.isNotEmpty()) 3.0 else 0.0) +
            (if (wish.strength > 0) 2.0 else 0.0) + wish.occasions.size * 2.0 + 0.0001
        return all.asSequence()
            .filter { r -> when (wish.alcohol) { "yes" -> r.isAlcoholic; "no" -> !r.isAlcoholic; else -> true } }
            .mapNotNull { r ->
                val m = Matcher.match(r, inventory, catalog)
                if (wish.maxMissing >= 0 && m.missingAfterSwaps > wish.maxMissing) return@mapNotNull null
                val ids = Matcher.requiredIds(r, catalog).toSet()
                val reasons = ArrayList<String>()
                var score = 0.0
                val fl = r.flavors.toSet()
                wish.flavors.forEach { f -> if (f in fl || (f == "refreshing" && "fresh" in fl)) { score += 2; reasons += labels("flavor_$f") } }
                if (wish.spirits.isNotEmpty()) {
                    val hit = wish.spirits.firstOrNull { g -> ids.any { it in SPIRIT_GROUPS[g].orEmpty() } }
                    if (hit != null) { score += 3; reasons += labels("spirit_$hit") }
                    else if (wish.alcohol != "no") score -= 2
                }
                if (wish.strength > 0) {
                    val s = strength(r)
                    if (s == wish.strength) score += 2 else if (kotlin.math.abs(s - wish.strength) == 1) score += 0.5 else score -= 1
                }
                wish.occasions.forEach { o ->
                    val (st, fv) = OCCASIONS[o] ?: return@forEach
                    if (r.styles.any { it in st } || fl.any { it in fv }) { score += 2; reasons += labels("occasion_$o") }
                }
                // small nudges: curated data, makeable, photos
                if (r.source == RecipeSource.LOCAL || r.source == RecipeSource.CUSTOM) score += 0.3
                if (m.missingCount == 0) { score += 0.6; reasons += labels("can_make") }
                if (r.imageUrl != null) score += 0.1
                Suggestion(r, score, ((score / maxScore) * 100).toInt().coerceIn(0, 100), reasons.distinct(), m.missingAfterSwaps)
            }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(n).toList()
    }

    /** Recommendations from the user's favourites: builds a taste profile and scores everything else. */
    fun forYou(favorites: List<Recipe>, all: List<Recipe>, catalog: Catalog, n: Int = 20): List<Recipe> {
        if (favorites.isEmpty()) return emptyList()
        val flavor = HashMap<String, Double>()
        val style = HashMap<String, Double>()
        val ing = HashMap<String, Double>()
        favorites.forEach { r ->
            r.flavors.forEach { flavor[it] = (flavor[it] ?: 0.0) + 1 }
            r.styles.forEach { style[it] = (style[it] ?: 0.0) + 1 }
            Matcher.requiredIds(r, catalog).forEach { ing[it] = (ing[it] ?: 0.0) + 1 }
        }
        val favIds = favorites.map { it.id }.toSet()
        val favNames = favorites.map { Catalog.norm(it.name) }.toSet()
        return all.asSequence()
            .filter { it.id !in favIds && Catalog.norm(it.name) !in favNames }
            .map { r ->
                val s = r.flavors.sumOf { flavor[it] ?: 0.0 } * 1.0 +
                    r.styles.sumOf { style[it] ?: 0.0 } * 0.8 +
                    Matcher.requiredIds(r, catalog).sumOf { ing[it] ?: 0.0 } * 1.2 +
                    (if (r.source == RecipeSource.LOCAL) 0.5 else 0.0) + (if (r.imageUrl != null) 0.3 else 0.0)
                r to s / (r.ingredients.size.coerceAtLeast(3))
            }
            .sortedByDescending { it.second }
            .take(n).map { it.first }.toList()
    }
}
