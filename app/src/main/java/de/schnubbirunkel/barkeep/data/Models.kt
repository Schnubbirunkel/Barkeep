package de.schnubbirunkel.barkeep.data

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedText(val en: String = "", val de: String = "") {
    fun get(lang: String): String = if (lang == "de" && de.isNotBlank()) de else en.ifBlank { de }
}

@Serializable
data class RecipeIngredient(
    /** Catalog key (see ingredients.json) – null for free-text ingredients. */
    val key: String? = null,
    /** Free-text name for online / custom / AI recipes. */
    val name: String? = null,
    val amount: Double? = null,
    val unit: String = "",
    /** "" normal, "t" top up, "f" float, "m" muddle, "r" rinse */
    val flag: String = "",
    /** Raw measure text if it could not be parsed. */
    val measure: String? = null,
)

object RecipeSource {
    const val LOCAL = "local"
    const val ONLINE = "online"
    const val CUSTOM = "custom"
}

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val nameDe: String? = null,
    val type: String = "cocktail",
    val styles: List<String> = emptyList(),
    val glass: String = "rocks",
    val method: String = "",
    val color: String = "#E8C77A",
    val flavors: List<String> = emptyList(),
    val season: List<String> = emptyList(),
    val servings: Int = 1,
    val difficulty: Int = 1,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val garnish: LocalizedText? = null,
    val about: LocalizedText? = null,
    val steps: Map<String, List<String>> = emptyMap(),
    val imageUrl: String? = null,
    val source: String = RecipeSource.LOCAL,
    val basedOn: String? = null,
    val notes: String = "",
) {
    fun displayName(lang: String) = if (lang == "de" && !nameDe.isNullOrBlank()) nameDe else name
    fun stepsFor(lang: String): List<String> = steps[lang]?.takeIf { it.isNotEmpty() } ?: steps["en"] ?: steps.values.firstOrNull() ?: emptyList()
    val isAlcoholic: Boolean get() = type != "mocktail" && type != "smoothie"
}

@Serializable
data class ShoppingItem(val id: Long, val text: String, val done: Boolean = false, val ingredientId: String? = null)

@Serializable
data class Bottle(
    val id: Long,
    val name: String,
    val brand: String = "",
    /** Catalog key (or "x:free text") this bottle counts as in recipes. */
    val ingredientId: String,
    val barcode: String? = null,
    /** 0..100 */
    val fill: Int = 100,
    val volumeMl: Int? = null,
    val imageUrl: String? = null,
    val addedAt: Long = 0,
)

@Serializable
data class Settings(
    val unitSystem: String = "ml", // ml, cl, oz
    val useOnline: Boolean = true,
    val cocktailDbKey: String = "1",
    val aiProvider: String = "gemini", // gemini, anthropic, openai
    val aiKey: String = "",
    val aiModel: String = "",
    val showAlcoholic: Boolean = true,
    val beginnerMode: Boolean = true,
    /** Downloaded sources the user switched on (see SourceIds). */
    val sources: Set<String> = emptySet(),
)

@Serializable
data class UserData(
    val inventory: Set<String> = emptySet(),
    val folders: Map<String, List<String>> = mapOf(DEFAULT_FOLDER to emptyList()),
    val custom: List<Recipe> = emptyList(),
    /** Online recipes the user saved, kept so they work offline. */
    val savedOnline: List<Recipe> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val bottles: List<Bottle> = emptyList(),
    /** Barcode -> ingredient id chosen by the user for products the lookup did not know. */
    val barcodeMap: Map<String, String> = emptyMap(),
    val settings: Settings = Settings(),
    val onboarded: Boolean = false,
) {
    /** Everything available for mixing: ticked ingredients plus non-empty bottles. */
    val available: Set<String> get() = inventory + bottles.filter { it.fill > 0 }.map { it.ingredientId }

    companion object { const val DEFAULT_FOLDER = "★" }
}

@Serializable
data class CatalogIngredient(val key: String, val en: String, val de: String, val category: String)

@Serializable
data class Substitution(val key: String, val en: String, val de: String)

@Serializable
data class CatalogFile(
    val ingredients: List<CatalogIngredient>,
    val aliases: Map<String, String>,
    val substitutions: Map<String, List<Substitution>>,
    val glasses: Map<String, LocalizedText>,
)
