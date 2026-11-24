package de.schnubbirunkel.barkeep.ai

import android.graphics.Bitmap
import android.util.Base64
import de.schnubbirunkel.barkeep.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AiIngredient(val name: String = "", val amount: Double? = null, val unit: String = "")

@Serializable
data class CocktailGuess(
    val isDrink: Boolean = true,
    val name: String = "",
    val confidence: Double = 0.0,
    val history: String = "",
    val glass: String = "",
    val ingredients: List<AiIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
)

@Serializable
data class IngredientGuess(val ingredients: List<String> = emptyList())

class AiException(message: String) : Exception(message)

/**
 * Image recognition through the user's own API key.
 * Supported: Google Gemini, Anthropic Claude, OpenAI.
 */
class VisionClient(
    private val provider: String,
    private val apiKey: String,
    private val model: String,
) {
    companion object {
        fun defaultModel(provider: String) = when (provider) {
            "anthropic" -> "claude-haiku-5-5"
            "openai" -> "gpt-4.1-mini"
            else -> "gemini-2.5-flash"
        }

        fun encode(bitmap: Bitmap, maxSide: Int = 1024): String {
            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
            val b = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
            val out = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, 82, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        /** Finds the first JSON object in a model answer (handles ```json fences). */
        fun extractJson(text: String): String {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw AiException("No JSON in answer")
            return text.substring(start, end + 1)
        }
    }

    private val usedModel get() = model.ifBlank { defaultModel(provider) }

    suspend fun identifyCocktail(imageB64: String, lang: String): CocktailGuess {
        val language = if (lang == "de") "German" else "English"
        val prompt = """
            You are an expert bartender. Identify the drink in this photo.
            Answer ONLY with JSON:
            {"isDrink": true|false, "name": "common English name of the drink", "confidence": 0.0-1.0,
             "history": "2-3 sentences about origin and history in $language",
             "glass": "glass type in English",
             "ingredients": [{"name": "ingredient in English", "amount": number in ml or pieces, "unit": "ml|dash|tsp|pc|slice|leaf"}],
             "steps": ["short preparation steps in $language"]}
            If it is not a drink, set isDrink to false and leave other fields empty.
        """.trimIndent()
        return AppJson.decodeFromString(CocktailGuess.serializer(), extractJson(ask(prompt, imageB64)))
    }

    suspend fun identifyIngredients(imageB64: String): IngredientGuess {
        val prompt = """
            List every bottle, juice, fruit, herb, syrup or other cocktail ingredient visible in this photo.
            Use generic English ingredient names (e.g. "gin", "sweet vermouth", "lime", "simple syrup", "tonic water");
            for branded bottles use the category (e.g. "Campari" stays "Campari", "Bacardi" becomes "white rum").
            Answer ONLY with JSON: {"ingredients": ["..."]}
        """.trimIndent()
        return AppJson.decodeFromString(IngredientGuess.serializer(), extractJson(ask(prompt, imageB64)))
    }

    /** Turns a free-text wish ("something fresh with gin, not too sweet") into structured criteria. */
    suspend fun parseWish(text: String): de.schnubbirunkel.barkeep.data.Wish {
        val prompt = """
            A user describes what drink they want: "${text.replace("\"", "'")}"
            Translate this into JSON with exactly these fields:
            {"alcohol": "yes"|"no"|"any",
             "flavors": subset of ["sweet","sour","bitter","fruity","refreshing","creamy","herbal","smoky","spicy","minty","coffee","floral"],
             "spirits": subset of ["gin","vodka","rum","agave","whiskey","brandy","wine"],
             "strength": 0 (any) | 1 (light) | 2 (medium) | 3 (strong),
             "occasions": subset of ["aperitif","summer","party","dessert","cozy","classic","tiki"],
             "maxMissing": -1}
            Answer ONLY with the JSON.
        """.trimIndent()
        return AppJson.decodeFromString(de.schnubbirunkel.barkeep.data.Wish.serializer(), extractJson(ask(prompt, null)))
    }

    private suspend fun ask(prompt: String, imageB64: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AiException("NO_KEY")
        when (provider) {
            "anthropic" -> anthropic(prompt, imageB64)
            "openai" -> openai(prompt, imageB64)
            else -> gemini(prompt, imageB64)
        }
    }

    private fun gemini(prompt: String, img: String?): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        if (img != null) addJsonObject { putJsonObject("inline_data") { put("mime_type", "image/jpeg"); put("data", img) } }
                    }
                }
            }
            putJsonObject("generationConfig") { put("responseMimeType", "application/json"); put("temperature", 0.2) }
        }
        val res = post("https://generativelanguage.googleapis.com/v1beta/models/$usedModel:generateContent",
            body.toString(), mapOf("x-goog-api-key" to apiKey))
        val root = AppJson.parseToJsonElement(res).jsonObject
        return root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.joinToString("") { (it.jsonObject["text"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
            ?: throw AiException("Empty answer")
    }

    private fun anthropic(prompt: String, img: String?): String {
        val body = buildJsonObject {
            put("model", usedModel)
            put("max_tokens", 1500)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        if (img != null) addJsonObject {
                            put("type", "image")
                            putJsonObject("source") { put("type", "base64"); put("media_type", "image/jpeg"); put("data", img) }
                        }
                        addJsonObject { put("type", "text"); put("text", prompt) }
                    }
                }
            }
        }
        val res = post("https://api.anthropic.com/v1/messages", body.toString(),
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"))
        val content = AppJson.parseToJsonElement(res).jsonObject["content"] as? JsonArray ?: throw AiException("Empty answer")
        return content.joinToString("") { (it.jsonObject["text"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
    }

    private fun openai(prompt: String, img: String?): String {
        val body = buildJsonObject {
            put("model", usedModel)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", prompt) }
                        if (img != null) addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$img") }
                        }
                    }
                }
            }
        }
        val res = post("https://api.openai.com/v1/chat/completions", body.toString(), mapOf("Authorization" to "Bearer $apiKey"))
        val root = AppJson.parseToJsonElement(res).jsonObject
        return root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull ?: throw AiException("Empty answer")
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching {
                    val o = AppJson.parseToJsonElement(text).jsonObject
                    (o["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw AiException("HTTP $code: ${msg ?: text.take(200)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
