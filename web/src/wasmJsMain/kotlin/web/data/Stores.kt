package de.schnubbirunkel.barkeep.data

import de.schnubbirunkel.barkeep.web.WebEnv
import de.schnubbirunkel.barkeep.web.jsLocalGet
import de.schnubbirunkel.barkeep.web.jsLocalSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/** Built-in data (preloaded from recipes.json / ingredients.json). */
class LocalData(@Suppress("UNUSED_PARAMETER") context: Any?) {
    val recipes: List<Recipe> = AppJson.decodeFromString<List<Recipe>>(WebEnv.recipesJson)
    val catalog: Catalog = Catalog(AppJson.decodeFromString<CatalogFile>(WebEnv.ingredientsJson))
}

/** User data persisted as JSON in localStorage. */
class UserStore(@Suppress("UNUSED_PARAMETER") context: Any?, fileName: String = "user.json") {
    private val key = "barkeep:$fileName"
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserData> = _state.asStateFlow()

    private fun load(): UserData {
        val text = jsLocalGet(key) ?: return UserData()
        return try {
            AppJson.decodeFromString<UserData>(text)
        } catch (e: Exception) {
            jsLocalSet("$key.broken", text)
            UserData()
        }
    }

    fun update(block: (UserData) -> UserData) {
        _state.update(block)
        jsLocalSet(key, AppJson.encodeToString(UserData.serializer(), _state.value))
    }

    fun export(): String = AppJson.encodeToString(UserData.serializer(), _state.value)

    fun import(text: String) {
        val data = AppJson.decodeFromString<UserData>(text)
        update { data }
    }
}
