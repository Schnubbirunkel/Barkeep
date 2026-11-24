package de.schnubbirunkel.barkeep.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/** Built-in data shipped in assets. */
class LocalData(context: Context) {
    val recipes: List<Recipe> = context.assets.open("recipes.json").bufferedReader().use {
        AppJson.decodeFromString<List<Recipe>>(it.readText())
    }
    val catalog: Catalog = context.assets.open("ingredients.json").bufferedReader().use {
        Catalog(AppJson.decodeFromString<CatalogFile>(it.readText()))
    }
}

/** User data persisted as a single JSON file. */
class UserStore(context: Context, fileName: String = "user.json") {
    private val file = File(context.filesDir, fileName)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserData> = _state.asStateFlow()

    private fun load(): UserData = try {
        if (file.exists()) AppJson.decodeFromString<UserData>(file.readText()) else UserData()
    } catch (e: Exception) {
        file.renameTo(File(file.parentFile, file.name + ".broken"))
        UserData()
    }

    fun update(block: (UserData) -> UserData) {
        _state.update(block)
        val snapshot = _state.value
        scope.launch {
            writeLock.withLock {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(AppJson.encodeToString(UserData.serializer(), snapshot))
                tmp.renameTo(file)
            }
        }
    }

    fun export(): String = AppJson.encodeToString(UserData.serializer(), _state.value)

    fun import(text: String) {
        val data = AppJson.decodeFromString<UserData>(text)
        update { data }
    }
}
