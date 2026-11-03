package com.skrypko.dicefamiliar.ui.pool.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.dice.StreamSource
import com.skrypko.dicefamiliar.data.pool.PoolImportOutcome
import com.skrypko.dicefamiliar.data.pool.PoolRepository
import com.skrypko.dicefamiliar.domain.pool.Pool
import com.skrypko.dicefamiliar.domain.pool.PoolDisplayMode
import com.skrypko.dicefamiliar.domain.pool.PoolError
import com.skrypko.dicefamiliar.domain.pool.PoolItem
import com.skrypko.dicefamiliar.domain.pool.PoolLimits
import com.skrypko.dicefamiliar.domain.pool.PoolValidator
import com.skrypko.dicefamiliar.domain.pool.UniqueNameResolver
import com.skrypko.dicefamiliar.domain.pool.io.ImportedItem
import com.skrypko.dicefamiliar.domain.pool.io.PoolImportError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An item in the draft; [key] is stable while editing (database ids change on save). */
data class DraftItem(val key: Long, val item: PoolItem)

/** The item editor sheet: [key] is `null` for a new item. */
data class ItemEditorState(
    val key: Long? = null,
    val name: String = "",
    val imagePath: String? = null,
    val colorArgb: Int? = null,
    val error: PoolError? = null,
    val storingImage: Boolean = false,
) {
    val canSave: Boolean get() = error == null && name.isNotBlank() && !storingImage
}

/** A parsed import waiting for Extend / Replace. Its images are already stored. */
data class ImportPreview(val items: List<ImportedItem>, val images: Int, val missingImages: Int, val skippedFiles: Int)

data class PoolEditUiState(
    val loaded: Boolean = false,
    val poolId: Long = 0,
    val name: String = "",
    val showNameError: Boolean = false,
    val items: List<DraftItem> = emptyList(),
    val showNames: Boolean = true,
    val editor: ItemEditorState? = null,
    val importing: Boolean = false,
    val preview: ImportPreview? = null,
    val dirty: Boolean = false,
    val imageDir: File = File(""),
) {
    val isNew: Boolean get() = poolId == 0L
    val domainItems: List<PoolItem> get() = items.map { it.item }
    val displayMode: PoolDisplayMode get() = PoolDisplayMode.of(domainItems, showNames)
    val canHideNames: Boolean get() = PoolDisplayMode.canHideNames(domainItems)
    val hasImages: Boolean get() = items.any { it.item.imagePath != null }
    val nameError: Boolean get() = showNameError && name.isBlank()
}

sealed interface PoolEditEvent {
    data class Saved(val poolId: Long, val hasItems: Boolean) : PoolEditEvent
    data object Discarded : PoolEditEvent
    data class Imported(val items: Int, val renamed: Int, val imagesSkipped: Int) : PoolEditEvent
    data class ImportFailed(val error: PoolImportError) : PoolEditEvent
    data object ImageFailed : PoolEditEvent
    data object TooManyItems : PoolEditEvent
    data class Exported(val ok: Boolean) : PoolEditEvent
}

@HiltViewModel
class PoolEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PoolRepository,
) : ViewModel() {
    private val poolId: Long = savedStateHandle.get<Long>(ARG_POOL_ID) ?: 0L

    private val _uiState = MutableStateFlow(PoolEditUiState(loaded = poolId == 0L, poolId = poolId, imageDir = repository.imageDir))
    val uiState: StateFlow<PoolEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<PoolEditEvent>(Channel.BUFFERED)
    val events: Flow<PoolEditEvent> = _events.receiveAsFlow()

    /** Images stored during this edit; the ones not saved are deleted when leaving. */
    private val sessionImages = mutableSetOf<String>()
    private var nextKey = 1L

    init {
        if (poolId != 0L) {
            viewModelScope.launch {
                val pool = repository.pool(poolId)
                _uiState.update {
                    it.copy(
                        loaded = true,
                        name = pool?.name.orEmpty(),
                        showNames = pool?.showNames ?: true,
                        items = pool?.items.orEmpty().map { item -> DraftItem(nextKey++, item) },
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) = edit { it.copy(name = name.take(PoolLimits.MAX_NAME_LENGTH)) }

    fun onShowNamesChange(show: Boolean) = edit { it.copy(showNames = show) }

    /** Moves an item up (-1) or down (+1). */
    fun move(key: Long, delta: Int) = edit { state ->
        val from = state.items.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in state.items.indices) return@edit state
        state.copy(items = state.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    // --- Item editor ---------------------------------------------------------------------------

    fun addItem() {
        if (_uiState.value.items.size >= PoolLimits.MAX_ITEMS) {
            _events.trySend(PoolEditEvent.TooManyItems)
            return
        }
        _uiState.update { it.copy(editor = ItemEditorState()) }
    }

    fun editItem(key: Long) {
        val draft = _uiState.value.items.firstOrNull { it.key == key } ?: return
        _uiState.update {
            it.copy(editor = ItemEditorState(key, draft.item.name, draft.item.imagePath, draft.item.colorArgb))
        }
    }

    fun onEditorNameChange(name: String) = updateEditor { editor ->
        val text = name.take(PoolLimits.MAX_NAME_LENGTH)
        editor.copy(name = text, error = PoolValidator.checkItemName(text, otherNames(editor.key)))
    }

    fun onEditorColorChange(argb: Int?) = updateEditor { it.copy(colorArgb = argb) }

    fun onEditorImagePicked(source: StreamSource) {
        updateEditor { it.copy(storingImage = true) }
        viewModelScope.launch {
            val path = repository.storeImage(source)
            if (path == null) _events.trySend(PoolEditEvent.ImageFailed) else sessionImages += path
            updateEditor { it.copy(storingImage = false, imagePath = path ?: it.imagePath) }
        }
    }

    fun onEditorRemoveImage() = updateEditor { it.copy(imagePath = null) }

    fun dismissEditor() = _uiState.update { it.copy(editor = null) }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        val error = PoolValidator.checkItemName(editor.name, otherNames(editor.key))
        if (error != null || editor.storingImage) {
            updateEditor { it.copy(error = error) }
            return
        }
        edit { state ->
            val item = PoolItem(name = editor.name.trim(), imagePath = editor.imagePath, colorArgb = editor.colorArgb)
            val items = if (editor.key == null) {
                state.items + DraftItem(nextKey++, item)
            } else {
                state.items.map { if (it.key == editor.key) it.copy(item = it.item.copy(name = item.name, imagePath = item.imagePath, colorArgb = item.colorArgb)) else it }
            }
            state.copy(items = items, editor = null)
        }
    }

    fun deleteEditorItem() {
        val key = _uiState.value.editor?.key ?: return dismissEditor()
        edit { state -> state.copy(items = state.items.filterNot { it.key == key }, editor = null) }
    }

    // --- Import ----------------------------------------------------------------------------------

    fun import(source: StreamSource, fileName: String?, mime: String?) {
        if (_uiState.value.importing) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch {
            when (val outcome = repository.readImport(source, fileName, mime)) {
                is PoolImportOutcome.Failed -> {
                    _uiState.update { it.copy(importing = false) }
                    _events.trySend(PoolEditEvent.ImportFailed(outcome.error))
                }
                is PoolImportOutcome.Ready -> {
                    sessionImages += outcome.items.mapNotNull { it.image }
                    _uiState.update {
                        it.copy(importing = false, preview = ImportPreview(outcome.items, outcome.images, outcome.missingImages, outcome.skippedFiles))
                    }
                }
            }
        }
    }

    /** Applies the previewed import: [replace] drops the current items, otherwise the new ones are appended. */
    fun applyImport(replace: Boolean) {
        val preview = _uiState.value.preview ?: return
        val kept = if (replace) emptyList() else _uiState.value.items
        if (kept.size + preview.items.size > PoolLimits.MAX_ITEMS) {
            cancelImport()
            _events.trySend(PoolEditEvent.ImportFailed(PoolImportError.TOO_MANY_ITEMS))
            return
        }
        val resolver = UniqueNameResolver(kept.map { it.item.name })
        var renamed = 0
        val added = preview.items.map { imported ->
            val name = resolver.resolve(imported.name)
            if (name != imported.name.trim()) renamed++
            DraftItem(nextKey++, PoolItem(name = name, imagePath = imported.image, colorArgb = imported.colorArgb))
        }
        edit { it.copy(items = kept + added, preview = null) }
        _events.trySend(PoolEditEvent.Imported(added.size, renamed, preview.missingImages))
    }

    fun cancelImport() {
        val preview = _uiState.value.preview ?: return
        _uiState.update { it.copy(preview = null) }
        val paths = preview.items.mapNotNull { it.image }
        sessionImages -= paths.toSet()
        viewModelScope.launch { repository.discardImages(paths) }
    }

    // --- Export ----------------------------------------------------------------------------------

    /** Writes the current items to [open]'s stream: a ZIP with images, or a `name,color` CSV. */
    fun export(withImages: Boolean, open: () -> OutputStream?, announce: Boolean = true, onDone: (Boolean) -> Unit = {}) {
        val items = _uiState.value.domainItems
        viewModelScope.launch {
            val ok = try {
                val out = open()
                if (out == null) {
                    false
                } else {
                    out.use { repository.export(items, withImages, it) }
                    true
                }
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            if (announce || !ok) _events.trySend(PoolEditEvent.Exported(ok))
            onDone(ok)
        }
    }

    // --- Save / discard --------------------------------------------------------------------------

    fun save() {
        val state = _uiState.value
        val pool = Pool(id = state.poolId, name = state.name.trim(), items = state.domainItems, showNames = state.showNames)
        val problems = PoolValidator.validate(pool)
        if (problems.isNotEmpty()) {
            _uiState.update { it.copy(showNameError = true) }
            return
        }
        viewModelScope.launch {
            val id = repository.save(pool)
            val kept = pool.items.mapNotNull { it.imagePath }.toSet()
            repository.discardImages(sessionImages - kept)
            sessionImages.clear()
            _uiState.update { it.copy(poolId = id, dirty = false) }
            _events.trySend(PoolEditEvent.Saved(id, pool.items.isNotEmpty()))
        }
    }

    /** Leaves without saving: images added in this edit are deleted. */
    fun discard() {
        val paths = sessionImages.toList()
        sessionImages.clear()
        viewModelScope.launch {
            repository.discardImages(paths)
            _events.trySend(PoolEditEvent.Discarded)
        }
    }

    private fun otherNames(key: Long?) = _uiState.value.items.filter { it.key != key }.map { it.item.name }

    private fun updateEditor(change: (ItemEditorState) -> ItemEditorState) =
        _uiState.update { state -> state.editor?.let { state.copy(editor = change(it)) } ?: state }

    private fun edit(change: (PoolEditUiState) -> PoolEditUiState) = _uiState.update { change(it).copy(dirty = true) }

    companion object {
        /** The [com.skrypko.dicefamiliar.ui.navigation.PoolEditRoute] argument. */
        const val ARG_POOL_ID = "poolId"
    }
}
