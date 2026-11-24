package de.schnubbirunkel.barkeep.data

/** Ingredient catalog: names, categories, aliases, substitutions and glasses. */
class Catalog(file: CatalogFile) {
    val ingredients: List<CatalogIngredient> = file.ingredients
    private val byKey = file.ingredients.associateBy { it.key }
    private val aliases: Map<String, String> = buildMap {
        file.ingredients.forEach {
            put(norm(it.en), it.key)
            put(norm(it.de), it.key)
            put(norm(it.key.replace('_', ' ')), it.key)
        }
        file.aliases.forEach { (k, v) -> put(norm(k), v) }
    }
    private val subs = file.substitutions
    private val glasses = file.glasses

    fun get(key: String): CatalogIngredient? = byKey[key]

    /** Map free text to a catalog key, if possible. */
    fun resolve(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val n = norm(name)
        aliases[n]?.let { return it }
        val stripped = n.removePrefix("fresh ").removePrefix("frischer ").removePrefix("frische ")
        aliases[stripped]?.let { return it }
        aliases[n.removeSuffix("s")]?.let { return it }
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

        /** Ingredients that do not count when checking what can be made. */
        val PANTRY = setOf("ice", "crushed_ice", "water", "hot_water")

        val CATEGORY_ORDER = listOf("spirit", "liqueur", "wine", "mixer", "juice", "syrup", "bitters", "dairy", "fruit", "other")
    }
}
