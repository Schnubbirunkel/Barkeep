package de.schnubbirunkel.barkeep.data

/** Ingredient catalog: names, categories, aliases, substitutions and glasses. */
class Catalog(file: CatalogFile) {
    val ingredients: List<CatalogIngredient> = file.ingredients
    private val byKey = file.ingredients.associateBy { it.key }
    private val aliases: Map<String, String> = buildMap {
        file.ingredients.forEach {
            put(spelling(norm(it.en)), it.key)
            put(spelling(norm(it.de)), it.key)
            put(spelling(norm(it.key.replace('_', ' '))), it.key)
        }
        file.aliases.forEach { (k, v) -> put(spelling(norm(k)), v) }
    }
    /** Alias words sorted longest first, for "ends with" matching ("london dry gin" -> gin). */
    private val aliasBySuffix: List<Pair<String, String>> = aliases.entries.filter { it.key.length >= 3 }
        .sortedByDescending { it.key.length }.map { it.key to it.value }
    private val resolveCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val termsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val subs = file.substitutions
    private val families = file.families
    private val familyWordsByKey: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        families.forEach { (word, keys) -> keys.forEach { k -> getOrPut(k) { mutableListOf() }.add(word) } }
    }
    private val aliasWordsByKey: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        file.aliases.forEach { (a, k) -> getOrPut(k) { mutableListOf() }.add(norm(a)) }
    }

    /** All words an ingredient can be found by (names, aliases, family words like "whisky"). */
    fun terms(key: String): String = termsCache.getOrPut(key) {
        val c = byKey[key]
        if (c == null) spelling(norm(key.removePrefix("x:")))
        else spelling((listOf(norm(c.en), norm(c.de), key.replace('_', ' ')) + aliasWordsByKey[key].orEmpty() + familyWordsByKey[key].orEmpty())
            .joinToString(" | "))
    }

    /** A brand/alias of [key] that matches the query but is not part of its display name (shown as a hint). */
    fun matchedAlias(key: String, query: String): String? {
        val q = spelling(norm(query))
        if (q.length < 2) return null
        val c = byKey[key] ?: return null
        if (spelling(norm(c.en)).contains(q) || spelling(norm(c.de)).contains(q)) return null
        return aliasWordsByKey[key].orEmpty().firstOrNull { spelling(it).contains(q) }
            ?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    /** Ingredient keys matching a free search query (handles whisky/whiskey, families, aliases). */
    fun search(query: String): Set<String> {
        val q = spelling(norm(query))
        if (q.isEmpty()) return ingredients.map { it.key }.toSet()
        return ingredients.filter { terms(it.key).contains(q) }.map { it.key }.toSet()
    }
    private val glasses = file.glasses

    fun get(key: String): CatalogIngredient? = byKey[key]

    /** Map free text to a catalog key, if possible (memoised – called for thousands of recipes). */
    fun resolve(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val cached = resolveCache[name]
        if (cached != null) return cached.ifEmpty { null }
        val r = resolveUncached(name)
        resolveCache[name] = r ?: ""
        return r
    }

    private val noise = Regex("\\b(fresh(ly)?( squeezed)?|chilled|cold|homemade|home-made|premium|good quality|quality|organic|frischer?|frisch gepresst(er)?|selbstgemacht(er)?)\\b")

    private fun resolveUncached(raw: String): String? {
        val n = spelling(norm(raw))
        aliases[n]?.let { return it }
        val cleaned = n.replace(Regex("\\(.*?\\)"), " ").replace(noise, " ").replace(Regex("[^a-z0-9 &'.-]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        aliases[cleaned]?.let { return it }
        if (cleaned.endsWith("s")) aliases[cleaned.dropLast(1)]?.let { return it }
        if (cleaned.endsWith("es")) aliases[cleaned.dropLast(2)]?.let { return it }
        // "london dry gin" -> "gin", "angostura aromatic bitters" -> "bitters", "chilled soda water" -> "soda water"
        for ((alias, key) in aliasBySuffix) {
            if (cleaned.endsWith(" $alias") || cleaned.startsWith("$alias ")) return key
        }
        return null
    }

    /** Stable id used for inventory and matching. */
    fun idOf(ing: RecipeIngredient): String = ing.key ?: resolve(ing.name) ?: freeId(ing.name ?: "?")

    fun freeId(name: String) = "x:" + norm(name)

    fun idForText(text: String): String = resolve(text) ?: freeId(text)

    fun name(id: String, lang: String): String {
        byKey[id]?.let { return if (lang == "de") it.de else it.en }
        return id.removePrefix("x:").replaceFirstChar { it.uppercase() }
    }

    fun name(ing: RecipeIngredient, lang: String): String {
        ing.key?.let { return name(it, lang) }
        val raw = ing.name ?: return "?"
        if (lang == "de") resolve(raw)?.let { return name(it, lang) }
        return raw
    }

    fun category(id: String): String = byKey[id]?.category ?: "other"

    fun substitutes(id: String): List<Substitution> = subs[id].orEmpty()

    fun glass(key: String, lang: String): String = glasses[key]?.get(lang) ?: key

    companion object {
        fun norm(s: String) = s.trim().lowercase()
            .replace('’', '\'')
            .replace(Regex("\\s+"), " ")

        /** Folds common spelling variants so either form matches. */
        fun spelling(s: String) = s.replace("whiskey", "whisky").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("é", "e").replace("ç", "c").replace("ñ", "n").replace("à", "a")

        /** Ingredients that do not count when checking what can be made. */
        val PANTRY = setOf("ice", "crushed_ice", "water", "hot_water")

        val CATEGORY_ORDER = listOf("spirit", "liqueur", "wine", "mixer", "juice", "syrup", "bitters", "dairy", "fruit", "other")
    }
}
