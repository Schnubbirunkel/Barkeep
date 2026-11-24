package de.schnubbirunkel.barkeep.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

data class ProductInfo(
    val barcode: String,
    val name: String,
    val brand: String,
    val categories: List<String>,
    val quantityMl: Int?,
    val imageUrl: String?,
    /** Best guess for the catalog ingredient, null if unknown. */
    val ingredientId: String?,
)

/** Product lookup via Open Food Facts (open database, ODbL). */
class BarcodeLookup(private val catalog: Catalog) {

    suspend fun lookup(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/${barcode.filter { it.isDigit() }}.json" +
            "?fields=product_name,product_name_de,product_name_en,brands,categories_tags,quantity,image_front_small_url"
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000
            setRequestProperty("User-Agent", "Barkeep-Android - https://github.com/Schnubbirunkel/Barkeep")
        }
        try {
            if (c.responseCode == 404) return@withContext null
            val root = AppJson.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }).jsonObject
            val p = root["product"] as? JsonObject ?: return@withContext null
            fun s(k: String) = (p[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val name = s("product_name_de").ifEmpty { s("product_name") }.ifEmpty { s("product_name_en") }
            val brand = s("brands").split(',').firstOrNull()?.trim().orEmpty()
            val cats = (p["categories_tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
            ProductInfo(
                barcode = barcode, name = name.ifEmpty { brand }, brand = brand, categories = cats,
                quantityMl = parseQuantity(s("quantity")), imageUrl = s("image_front_small_url").ifEmpty { null },
                ingredientId = guess(cats, "$name $brand"),
            )
        } finally { c.disconnect() }
    }

    /** Maps Open Food Facts categories and the product name to an ingredient key. */
    fun guess(categories: List<String>, name: String): String? {
        val n = " " + Catalog.norm(name) + " "
        val tags = categories.joinToString(" ") { it.substringAfter(':') }
        fun any(vararg w: String) = w.any { it in tags || " $it " in n || it in n }
        catalog.resolve(name)?.let { return it }
        return when {
            any("islay") -> "islay_scotch"
            any("bourbon") -> "bourbon"
            any("rye-whisk", "rye whisk") -> "rye"
            any("irish-whiskey", "irish whiskey") -> "irish_whiskey"
            any("scotch", "whisky", "whiskies", "single malt") -> "scotch"
            any("whiskey") -> "bourbon"
            any("mezcal") -> "mezcal"
            any("reposado", "anejo", "añejo") -> "reposado"
            any("tequila") -> "tequila"
            any("cachaca", "cachaça") -> "cachaca"
            any("spiced-rum", "dark-rum", "dark rum", "spiced", "black rum") -> "dark_rum"
            any("aged-rum", "anejo rum", "gold rum", "rhum vieux", "reserva") && any("rum", "rhum", "ron") -> "aged_rum"
            any("rums", "rum", "rhum", " ron ") -> "white_rum"
            any("sloe") -> "sloe_gin"
            any("gins", "gin ") -> "gin"
            any("vodka", "wodka") -> "vodka"
            any("cognac") -> "cognac"
            any("calvados") -> "calvados"
            any("pisco") -> "pisco"
            any("brandies", "brandy", "weinbrand") -> "brandy"
            any("absinth") -> "absinthe"
            any("campari") -> "campari"
            any("aperol") -> "aperol"
            any("cointreau") -> "cointreau"
            any("triple-sec", "triple sec") -> "triple_sec"
            any("curacao", "curaçao") -> if (any("blue")) "blue_curacao" else "orange_curacao"
            any("amaretto") -> "amaretto"
            any("coffee-liqueur", "kaffeelik", "kahl") -> "coffee_liqueur"
            any("cream-liqueur", "irish cream", "baileys") -> "irish_cream"
            any("limoncello") -> "limoncello"
            any("elderflower", "holunder", "st-germain") -> if (any("liqueur", "likör")) "elderflower_liqueur" else "elderflower_syrup"
            any("jager", "jäger", "krauterlik", "kräuterlik", "herbal-liqueur") -> "jagermeister"
            any("chartreuse") -> if (any("yellow", "jaune")) "yellow_chartreuse" else "green_chartreuse"
            any("maraschino") -> "maraschino"
            any("galliano") -> "galliano"
            any("vermouth", "wermut", "wermuth") -> when {
                any("dry", "extra dry", "trocken") -> "dry_vermouth"
                any("bianco", "blanc") -> "blanc_vermouth"
                else -> "sweet_vermouth"
            }
            any("lillet") -> "lillet"
            any("prosecco") -> "prosecco"
            any("champagne", "champagner") -> "champagne"
            any("sparkling-wine", "sekt", "cava", "cremant", "crémant") -> "sparkling_wine"
            any("port-wine", "portwein", " port ") -> "port"
            any("sherr") -> "dry_sherry"
            any("red-wine", "rotwein") -> "red_wine"
            any("white-wine", "weisswein", "weißwein") -> "white_wine"
            any("tonic") -> "tonic"
            any("ginger-beer", "ginger beer") -> "ginger_beer"
            any("ginger-ale", "ginger ale") -> "ginger_ale"
            any("colas", "cola") -> "cola"
            any("grenadine") -> "grenadine"
            any("bitters", "angostura") -> "angostura"
            any("syrup", "sirup") -> "simple_syrup"
            any("beer", "bier") -> "beer"
            any("liqueur", "likör", "likoer") -> null
            else -> null
        }
    }

    private fun parseQuantity(q: String): Int? {
        val m = Regex("([\\d.,]+)\\s*(ml|cl|l)", RegexOption.IGNORE_CASE).find(q) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) { "l" -> v * 1000; "cl" -> v * 10; else -> v }.toInt()
    }
}
