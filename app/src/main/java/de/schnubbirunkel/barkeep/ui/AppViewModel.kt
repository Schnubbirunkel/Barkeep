package de.schnubbirunkel.barkeep.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.schnubbirunkel.barkeep.ai.AiException
import de.schnubbirunkel.barkeep.ai.CocktailGuess
import de.schnubbirunkel.barkeep.ai.VisionClient
import de.schnubbirunkel.barkeep.data.Catalog
import de.schnubbirunkel.barkeep.data.CocktailDb
import de.schnubbirunkel.barkeep.data.BarcodeLookup
import de.schnubbirunkel.barkeep.data.Bottle
import de.schnubbirunkel.barkeep.data.ProductInfo
import de.schnubbirunkel.barkeep.data.DownloadedSources
import de.schnubbirunkel.barkeep.data.SourceIds
import de.schnubbirunkel.barkeep.data.LocalData
import de.schnubbirunkel.barkeep.data.LocalizedText
import de.schnubbirunkel.barkeep.data.Recipe
import de.schnubbirunkel.barkeep.data.RecipeIngredient
import de.schnubbirunkel.barkeep.data.RecipeSource
import de.schnubbirunkel.barkeep.data.Settings
import de.schnubbirunkel.barkeep.data.ShoppingItem
import de.schnubbirunkel.barkeep.data.UserData
import de.schnubbirunkel.barkeep.data.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BarcodeState {
    data object Idle : BarcodeState
    data object Looking : BarcodeState
    /** product may be null when the barcode is unknown */
    data class Result(val barcode: String, val product: ProductInfo?, val ingredientId: String?) : BarcodeState
    data class Error(val message: String) : BarcodeState
}

sealed interface ScanState {
    data object Idle : ScanState
    data object Working : ScanState
    data class Cocktail(val guess: CocktailGuess, val match: Recipe?) : ScanState
    data class Ingredients(val ids: List<String>) : ScanState
    data class Error(val message: String) : ScanState
}

class AppViewModel @JvmOverloads constructor(
    app: Application,
    storeFile: String = "user.json",
    private val autoDownload: Boolean = true,
) : AndroidViewModel(app) {
    private val local = LocalData(app)
    val catalog: Catalog = local.catalog
    private val store = UserStore(app, storeFile)
    val user: StateFlow<UserData> = store.state
    private val cdb = CocktailDb { user.value.settings.cocktailDbKey }

    /** Online recipes seen in this session (search results, lookups). */
    private val onlineCache = mutableMapOf<String, Recipe>()

    private val downloads = DownloadedSources(app, catalog)
    private val sourceData = MutableStateFlow<Map<String, List<Recipe>>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            sourceData.value = listOf(SourceIds.COCKTAILDB, SourceIds.COMMUNITY)
                .filter { downloads.isDownloaded(it) }.associateWith { downloads.load(it) }
        }
    }

    val allRecipes: StateFlow<List<Recipe>> = combine(user, sourceData) { u, src -> buildAll(u, src) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildAll(user.value, emptyMap()))

    private fun buildAll(u: UserData, src: Map<String, List<Recipe>>): List<Recipe> {
        // Photos from TheCocktailDB are reused for every recipe with the same name.
        val photos = HashMap<String, String>()
        (src[SourceIds.COCKTAILDB].orEmpty() + u.savedOnline).forEach { r -> r.imageUrl?.let { photos.putIfAbsent(photoKey(r.name), it) } }
        val seen = HashSet<String>()
        val out = ArrayList<Recipe>()
        fun addAll(list: List<Recipe>) = list.forEach { r ->
            if (seen.add(Catalog.norm(r.name))) {
                val photo = if (r.imageUrl == null) photos[photoKey(r.name)] ?: r.nameDe?.let { photos[photoKey(it)] } else null
                out += if (photo != null) r.copy(imageUrl = photo) else r
            }
        }
        addAll(u.custom)
        addAll(local.recipes)
        if (SourceIds.COCKTAILDB in u.settings.sources) addAll(src[SourceIds.COCKTAILDB].orEmpty())
        addAll(u.savedOnline)
        if (SourceIds.COMMUNITY in u.settings.sources) addAll(src[SourceIds.COMMUNITY].orEmpty())
        return if (u.settings.showAlcoholic) out else out.filter { !it.isAlcoholic }
    }

    private fun photoKey(name: String) = Catalog.spelling(Catalog.norm(name)).replace(Regex("[^a-z0-9]"), "")

    // ---------- downloadable sources ----------
    /** source id -> progress 0..1, or null if idle */
    private val _downloading = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloading: StateFlow<Map<String, Float>> = _downloading.asStateFlow()
    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()
    fun consumeDownloadMessage() { _downloadMessage.value = null }

    fun sourceCount(id: String): Int = sourceData.value[id]?.size ?: 0

    fun setSource(id: String, enabled: Boolean) {
        if (!enabled) {
            updateSettings { it.copy(sources = it.sources - id) }
            return
        }
        if (downloads.isDownloaded(id) && sourceData.value[id].orEmpty().isNotEmpty()) {
            updateSettings { it.copy(sources = it.sources + id) }
            return
        }
        downloadSource(id)
    }

    fun downloadSource(id: String, quiet: Boolean = false) {
        if (id in _downloading.value) return
        _downloading.value = _downloading.value + (id to 0f)
        viewModelScope.launch {
            try {
                val progress: (Float) -> Unit = { p -> _downloading.value = _downloading.value + (id to p) }
                val n = if (id == SourceIds.COCKTAILDB) downloads.downloadCocktailDb(user.value.settings.cocktailDbKey, progress)
                else downloads.downloadCommunity(progress)
                val list = withContext(Dispatchers.IO) { downloads.load(id) }
                sourceData.value = sourceData.value + (id to list)
                updateSettings { it.copy(sources = it.sources + id) }
                if (!quiet) _downloadMessage.value = "OK:$n"
            } catch (e: Exception) {
                if (!quiet) _downloadMessage.value = "ERR:" + (e.message ?: e.javaClass.simpleName)
            } finally {
                _downloading.value = _downloading.value - id
            }
        }
    }

    fun deleteSource(id: String) {
        downloads.delete(id)
        sourceData.value = sourceData.value - id
        updateSettings { it.copy(sources = it.sources - id) }
    }

    val localRecipes: List<Recipe> get() = local.recipes

    fun recipe(id: String): Recipe? = allRecipes.value.firstOrNull { it.id == id } ?: rawRecipe(id)

    private fun rawRecipe(id: String): Recipe? =
        user.value.custom.firstOrNull { it.id == id }
            ?: local.recipes.firstOrNull { it.id == id }
            ?: user.value.savedOnline.firstOrNull { it.id == id }
            ?: onlineCache[id]
            ?: sourceData.value.values.firstNotNullOfOrNull { l -> l.firstOrNull { it.id == id } }

    suspend fun loadRecipe(id: String): Recipe? {
        recipe(id)?.let { if (it.ingredients.isNotEmpty()) return it }
        if (!id.startsWith(CocktailDb.PREFIX)) return null
        return runCatching { cdb.lookup(id) }.getOrNull()?.also { onlineCache[it.id] = it }
    }

    // ---------- online search ----------
    private val _online = MutableStateFlow<List<Recipe>>(emptyList())
    val onlineResults: StateFlow<List<Recipe>> = _online.asStateFlow()
    private val _onlineError = MutableStateFlow(false)
    val onlineError: StateFlow<Boolean> = _onlineError.asStateFlow()
    private var searchJob: Job? = null

    fun searchOnline(query: String) {
        searchJob?.cancel()
        if (!user.value.settings.useOnline || query.trim().length < 2) { _online.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(400)
            runCatching { cdb.search(query.trim()) }
                .onSuccess { list ->
                    _onlineError.value = false
                    list.forEach { onlineCache[it.id] = it }
                    val known = allRecipes.value.map { Catalog.norm(it.name) }.toSet()
                    _online.value = list.filter { Catalog.norm(it.name) !in known }
                }
                .onFailure { _onlineError.value = true; _online.value = emptyList() }
        }
    }

    suspend fun randomOnline(): Recipe? = runCatching { cdb.random() }.getOrNull()?.also { onlineCache[it.id] = it }

    // ---------- bar inventory ----------
    fun toggleInventory(id: String) = store.update {
        it.copy(inventory = if (id in it.inventory) it.inventory - id else it.inventory + id)
    }

    fun addInventory(ids: Collection<String>) = store.update { it.copy(inventory = it.inventory + ids) }
    fun clearInventory() = store.update { it.copy(inventory = emptySet()) }

    /** Removes an ingredient from the bar, including its bottles. */
    fun removeFromBar(id: String) = store.update { u ->
        u.copy(inventory = u.inventory - id, bottles = u.bottles.filter { it.ingredientId != id })
    }

    // ---------- bottles & barcode ----------
    private val barcodeLookup = BarcodeLookup(catalog)
    private val _barcode = MutableStateFlow<BarcodeState>(BarcodeState.Idle)
    val barcode: StateFlow<BarcodeState> = _barcode.asStateFlow()
    fun resetBarcode() { _barcode.value = BarcodeState.Idle }

    fun onBarcode(code: String) {
        _barcode.value = BarcodeState.Looking
        viewModelScope.launch {
            val known = user.value.barcodeMap[code]
            val product = runCatching { barcodeLookup.lookup(code) }
            product.onSuccess { p -> _barcode.value = BarcodeState.Result(code, p, known ?: p?.ingredientId) }
                .onFailure {
                    _barcode.value = if (known != null) BarcodeState.Result(code, null, known)
                    else BarcodeState.Error(it.message ?: it.javaClass.simpleName)
                }
        }
    }

    fun addBottle(name: String, brand: String, ingredientId: String, barcode: String? = null, volumeMl: Int? = null, imageUrl: String? = null) =
        store.update { u ->
            val now = System.currentTimeMillis()
            u.copy(
                bottles = u.bottles + Bottle(now + u.bottles.size, name.ifBlank { catalog.name(ingredientId, "en") }, brand, ingredientId, barcode, 100, volumeMl, imageUrl, now),
                barcodeMap = if (barcode != null) u.barcodeMap + (barcode to ingredientId) else u.barcodeMap,
            )
        }

    fun updateBottle(b: Bottle) = store.update { u -> u.copy(bottles = u.bottles.map { if (it.id == b.id) b else it }) }
    fun removeBottle(id: Long) = store.update { u -> u.copy(bottles = u.bottles.filter { it.id != id }) }

    /** Marks the bottle empty, removes it and puts a refill on the shopping list. */
    fun bottleEmpty(b: Bottle) {
        removeBottle(b.id)
        addShopping(listOf(b.brand, b.name).filter { it.isNotBlank() }.distinct().joinToString(" "), b.ingredientId)
    }

    // ---------- favourites / folders ----------
    fun folderOf(id: String): List<String> = user.value.folders.filter { id in it.value }.keys.toList()
    fun isFavorite(id: String) = user.value.folders.values.any { id in it }

    fun setInFolder(folder: String, recipe: Recipe, inFolder: Boolean) = store.update { u ->
        val list = u.folders[folder].orEmpty()
        val newList = if (inFolder) (list + recipe.id).distinct() else list - recipe.id
        var saved = u.savedOnline
        if (recipe.source != RecipeSource.LOCAL && recipe.source != RecipeSource.CUSTOM && inFolder && saved.none { it.id == recipe.id }) saved = saved + recipe
        val folders = u.folders + (folder to newList)
        if (recipe.source != RecipeSource.LOCAL && recipe.source != RecipeSource.CUSTOM && folders.values.none { recipe.id in it }) saved = saved.filter { it.id != recipe.id }
        u.copy(folders = folders, savedOnline = saved)
    }

    fun toggleFavorite(recipe: Recipe) = setInFolder(UserData.DEFAULT_FOLDER, recipe, !isFavorite(recipe.id))

    fun createFolder(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        store.update { if (n in it.folders) it else it.copy(folders = it.folders + (n to emptyList())) }
    }

    fun deleteFolder(name: String) = store.update {
        if (name == UserData.DEFAULT_FOLDER) it.copy(folders = it.folders + (name to emptyList())) else it.copy(folders = it.folders - name)
    }

    fun renameFolder(old: String, new: String) = store.update {
        val n = new.trim()
        if (n.isEmpty() || n in it.folders || old == UserData.DEFAULT_FOLDER) it
        else it.copy(folders = it.folders.entries.associate { (k, v) -> (if (k == old) n else k) to v })
    }

    // ---------- custom recipes ----------
    fun saveCustom(recipe: Recipe) = store.update { u ->
        val r = recipe.copy(source = RecipeSource.CUSTOM)
        u.copy(custom = listOf(r) + u.custom.filter { it.id != r.id })
    }

    fun deleteCustom(id: String) = store.update { u ->
        u.copy(custom = u.custom.filter { it.id != id }, folders = u.folders.mapValues { (_, v) -> v - id })
    }

    // ---------- shopping list ----------
    fun addShopping(text: String, ingredientId: String? = null) {
        val t = text.trim()
        if (t.isEmpty()) return
        store.update { u ->
            if (u.shopping.any { (ingredientId != null && it.ingredientId == ingredientId) || it.text.equals(t, true) }) u
            else u.copy(shopping = u.shopping + ShoppingItem(System.currentTimeMillis() + u.shopping.size, t, false, ingredientId))
        }
    }

    fun addMissingToShopping(ids: List<String>, lang: String) = ids.forEach { addShopping(catalog.name(it, lang), it) }

    fun toggleShopping(id: Long) = store.update { u ->
        u.copy(shopping = u.shopping.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun removeShopping(id: Long) = store.update { u -> u.copy(shopping = u.shopping.filter { it.id != id }) }

    /** Moves checked items into the bar and removes them from the list. */
    fun checkoutShopping() = store.update { u ->
        val bought = u.shopping.filter { it.done }
        val ids = bought.map { it.ingredientId ?: catalog.idForText(it.text) }
        u.copy(
            shopping = u.shopping.filter { !it.done },
            inventory = u.inventory + ids.filter { id -> u.bottles.none { it.ingredientId == id } },
        )
    }

    // ---------- settings ----------
    fun updateSettings(block: (Settings) -> Settings) = store.update { it.copy(settings = block(it.settings)) }
    fun setOnboarded() = store.update { it.copy(onboarded = true) }
    fun exportData(): String = store.export()
    fun importData(text: String): Boolean = runCatching { store.import(text) }.isSuccess

    // ---------- AI photo recognition ----------
    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    fun resetScan() { _scan.value = ScanState.Idle }

    fun scanImage(bitmap: Bitmap, ingredientsMode: Boolean, lang: String) {
        val s = user.value.settings
        if (s.aiKey.isBlank() && s.aiProvider != "custom") { _scan.value = ScanState.Error("NO_KEY"); return }
        _scan.value = ScanState.Working
        viewModelScope.launch {
            try {
                val b64 = withContext(Dispatchers.Default) { VisionClient.encode(bitmap) }
                val client = VisionClient(s.aiProvider, s.aiKey, s.aiModel, s.aiBaseUrl)
                if (ingredientsMode) {
                    val g = client.identifyIngredients(b64)
                    _scan.value = ScanState.Ingredients(g.ingredients.map { catalog.idForText(it) }.distinct())
                } else {
                    val g = client.identifyCocktail(b64, lang)
                    val match = findByName(g.name)
                    _scan.value = ScanState.Cocktail(g, match)
                }
            } catch (e: AiException) {
                _scan.value = ScanState.Error(e.message ?: "?")
            } catch (e: Exception) {
                _scan.value = ScanState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Free-text wish -> criteria via the user's AI provider. */
    suspend fun parseWish(text: String): Result<de.schnubbirunkel.barkeep.data.Wish> = runCatching {
        val s = user.value.settings
        if (s.aiKey.isBlank() && s.aiProvider != "custom") throw AiException("NO_KEY")
        VisionClient(s.aiProvider, s.aiKey, s.aiModel, s.aiBaseUrl).parseWish(text)
    }

    suspend fun geminiModels(): Result<List<String>> = runCatching { VisionClient.listGeminiModels(user.value.settings.aiKey) }

    fun favoriteRecipes(): List<Recipe> = user.value.folders.values.flatten().distinct().mapNotNull { recipe(it) }

    fun findByName(name: String): Recipe? {
        val n = Catalog.norm(name)
        if (n.isEmpty()) return null
        return allRecipes.value.firstOrNull { Catalog.norm(it.name) == n || Catalog.norm(it.nameDe ?: "") == n }
            ?: allRecipes.value.firstOrNull { n.contains(Catalog.norm(it.name)) && it.name.length > 4 }
    }

    fun guessToRecipe(g: CocktailGuess, lang: String): Recipe = Recipe(
        id = "custom:" + System.currentTimeMillis(),
        name = g.name.ifBlank { "?" },
        glass = de.schnubbirunkel.barkeep.data.CocktailDb.mapGlass(g.glass),
        ingredients = g.ingredients.map {
            val key = catalog.resolve(it.name)
            RecipeIngredient(key = key, name = if (key == null) it.name else null, amount = it.amount, unit = it.unit.ifBlank { "ml" })
        },
        about = if (g.history.isNotBlank()) LocalizedText(en = g.history, de = g.history) else null,
        steps = mapOf(lang to g.steps),
        source = RecipeSource.CUSTOM,
    )

    init {
        // First start: fetch TheCocktailDB once so recipes get photos and the collection grows.
        val st = user.value.settings
        if (autoDownload && st.useOnline && !st.autoDownloadTried && !downloads.isDownloaded(SourceIds.COCKTAILDB)) {
            updateSettings { it.copy(autoDownloadTried = true) }
            downloadSource(SourceIds.COCKTAILDB, quiet = true)
        }
    }
}
