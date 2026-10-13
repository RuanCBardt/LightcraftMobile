package com.lightcraft.mobile

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

object NativeEngine {
    init { System.loadLibrary("lightcraft_mobile") }
    external fun execute(request: String): String
}

data class FilterPreset(val id: String, val name: String, val group: String)
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f)
data class EditState(
    val preset: String? = null,
    val amount: Float = 100f,
    val controls: Map<String, Float> = emptyMap(),
    val crop: CropRect = CropRect(),
    val rotation: Int = 0,
) {
    fun json() = JSONObject().apply {
        put("preset", preset ?: JSONObject.NULL); put("amount", amount)
        put("controls", JSONObject(controls)); put("rotation", rotation)
        put("crop", JSONArray(listOf(crop.left, crop.top, crop.right, crop.bottom)))
    }
    companion object {
        fun from(json: JSONObject): EditState {
            val controls = json.optJSONObject("controls") ?: JSONObject()
            val crop = json.optJSONArray("crop")
            return EditState(
                preset = json.optString("preset").takeIf { it.isNotEmpty() && it != "null" },
                amount = json.optDouble("amount", 100.0).toFloat(),
                controls = controls.keys().asSequence().associateWith { controls.getDouble(it).toFloat() },
                crop = if (crop != null && crop.length() == 4) CropRect(crop.getDouble(0).toFloat(), crop.getDouble(1).toFloat(), crop.getDouble(2).toFloat(), crop.getDouble(3).toFloat()) else CropRect(),
                rotation = json.optInt("rotation"),
            )
        }
    }
}

data class EditorUi(
    val ready: Boolean = false,
    val hasPhoto: Boolean = false,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val rendering: Boolean = false,
    val photoName: String = "",
    val preview: Bitmap? = null,
    val original: Bitmap? = null,
    val presets: List<FilterPreset> = emptyList(),
    val thumbnails: Map<String, Bitmap> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    val edit: EditState = EditState(),
    val effectiveControls: Map<String, Float> = emptyMap(),
    val tab: String = "Presets",
    val group: String = "Todos",
    val canUndo: Boolean = false,
    val editingAmount: Boolean = false,
    val message: String? = null,
)

fun EditorUi.visiblePresets(): List<FilterPreset> = presets.filter {
    group == "Todos" || (group == "Favoritos" && it.id in favorites) || it.group == group
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val engineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val state = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = state.asStateFlow()
    private val draft = File(app.filesDir, "draft.json")
    private val preferences = app.getSharedPreferences("editor", 0)
    private val history = ArrayDeque<EditState>()
    private var previewJob: Job? = null
    private var thumbnailJob: Job? = null
    private var source: File? = null
    private var revision = 0
    private var amountStart: EditState? = null

    init {
        viewModelScope.launch {
            try {
                val result = call(JSONObject().put("method", "init").put("folder", app.filesDir.absolutePath)) as JSONArray
                state.update { it.copy(ready = true, presets = parsePresets(result), favorites = preferences.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
                val saved = withContext(Dispatchers.IO) { if (draft.exists()) JSONObject(draft.readText()) else null }
                val savedSource = saved?.optString("source")?.let(::File)
                if (savedSource?.isFile == true) openFile(savedSource, saved.optString("name"), EditState.from(saved.getJSONObject("edit")))
            } catch (e: Exception) { error(e) }
        }
    }

    private suspend fun call(request: JSONObject): Any = withContext(engineDispatcher) {
        val reply = JSONObject(NativeEngine.execute(request.toString()))
        check(reply.optBoolean("ok")) { reply.optString("error", "Não foi possível concluir a operação") }
        reply.get("data")
    }

    private fun parsePresets(array: JSONArray) = (0 until array.length()).map {
        val p = array.getJSONObject(it)
        FilterPreset(p.getString("id"), p.getString("name"), p.getString("group"))
    }

    fun importPhoto(uri: Uri) = viewModelScope.launch {
        state.update { it.copy(loading = true) }
        try {
            val (file, name) = copyDocument(uri)
            openFile(file, name, EditState())
        } catch (e: Exception) { error(e) }
        finally { state.update { it.copy(loading = false) } }
    }

    private suspend fun copyDocument(uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        var name = "foto.jpg"
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val extension = name.substringAfterLast('.', "jpg").lowercase().filter { it.isLetterOrDigit() }.take(12)
        val destination = File(app.filesDir, "input-${UUID.randomUUID()}.$extension")
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 128L * 1024 * 1024) { "Escolha um arquivo de até 128 MB" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Não foi possível ler o arquivo")
            destination to name
        } catch (e: Exception) { destination.delete(); throw e }
    }

    private suspend fun openFile(file: File, name: String, edit: EditState) {
        state.update { it.copy(loading = true) }
        previewJob?.cancelAndJoin(); thumbnailJob?.cancelAndJoin(); revision++
        call(JSONObject().put("method", "open").put("path", file.absolutePath))
        source = file
        val original = renderBitmap(EditState(), 1440)
        history.clear()
        amountStart = null
        state.update { it.copy(hasPhoto = true, photoName = name, original = original.bitmap, preview = original.bitmap, edit = edit, effectiveControls = original.controls, thumbnails = emptyMap(), loading = false, canUndo = false, editingAmount = false) }
        saveDraft(edit)
        // Keep the current source for process-death restoration, remove old imported copies.
        withContext(Dispatchers.IO) { app.filesDir.listFiles()?.filter { it.name.startsWith("input-") && it != file }?.forEach { it.delete() } }
        requestPreview()
        refreshThumbnails()
    }

    fun importPresets(uris: List<Uri>) = viewModelScope.launch {
        state.update { it.copy(loading = true) }
        try {
            previewJob?.cancelAndJoin(); thumbnailJob?.cancelAndJoin()
            var imported = 0
            val warnings = mutableSetOf<String>()
            val failures = mutableListOf<String>()
            for (uri in uris) {
                val (file, _) = copyDocument(uri)
                try {
                    val data = call(JSONObject().put("method", "importPresets").put("path", file.absolutePath)) as JSONObject
                    val report = data.getJSONObject("report")
                    val list = report.getJSONArray("imported")
                    imported += list.length()
                    for (i in 0 until list.length()) {
                        val unmapped = list.getJSONObject(i).optJSONArray("unmapped") ?: continue
                        for (j in 0 until unmapped.length()) warnings.add(unmapped.getString(j))
                    }
                    val failed = report.optJSONArray("failed") ?: JSONArray()
                    for (i in 0 until failed.length()) failures.add(failed.getJSONArray(i).optString(1))
                    state.update { it.copy(presets = parsePresets(data.getJSONArray("presets")), group = "Todos") }
                } finally { withContext(Dispatchers.IO) { file.delete() } }
            }
            val message = buildString {
                append(if (imported == 0 && failures.isEmpty()) "Presets já importados" else "$imported preset(s) importado(s)")
                if (warnings.isNotEmpty()) append(". Ajustes não transferidos: ${warnings.joinToString()}")
                if (failures.isNotEmpty()) append(". ${failures.joinToString()}")
            }
            state.update { it.copy(message = message) }
            requestPreview(); refreshThumbnails()
        } catch (e: Exception) { error(e) }
        finally { state.update { it.copy(loading = false) } }
    }

    private data class Preview(val bitmap: Bitmap, val controls: Map<String, Float>)
    private suspend fun renderBitmap(edit: EditState, edge: Int): Preview {
        val file = File(app.cacheDir, "render-${UUID.randomUUID()}.jpg")
        try {
            val result = call(JSONObject().put("method", "render").put("edit", edit.json()).put("edge", edge).put("path", file.absolutePath)) as JSONObject
            val controls = result.getJSONObject("controls")
            return withContext(Dispatchers.IO) { Preview(checkNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Prévia indisponível" }, controls.keys().asSequence().associateWith { controls.getDouble(it).toFloat() }) }
        } finally { withContext(NonCancellable + Dispatchers.IO) { file.delete() } }
    }

    fun change(edit: EditState, remember: Boolean = true) {
        if (edit == state.value.edit) return
        if (remember) {
            if (history.size >= 40) history.removeFirst()
            history.addLast(state.value.edit)
        }
        state.update { it.copy(edit = edit, canUndo = history.isNotEmpty()) }
        requestPreview()
    }
    fun rememberGesture() {
        if (history.size >= 40) history.removeFirst()
        history.addLast(state.value.edit)
        state.update { it.copy(canUndo = true) }
    }
    fun undo() { if (history.isNotEmpty()) change(history.removeLast(), false) }
    fun reset() = change(EditState())
    fun selectPreset(id: String?) = change(state.value.edit.copy(preset = id, amount = 100f))
    /** One neighboring visible preset per completed horizontal swipe, stopping at either end. */
    fun stepPreset(direction: Int) {
        val snapshot = state.value
        if (!snapshot.hasPhoto || snapshot.loading || snapshot.exporting || snapshot.editingAmount || snapshot.tab == "Recortar") return
        val ids = listOf<String?>(null) + snapshot.visiblePresets().map { it.id }
        val index = ids.indexOf(snapshot.edit.preset)
        val next = if (index < 0) { if (direction > 0) 1 else 0 } else index + direction.coerceIn(-1, 1)
        if (next in ids.indices && next != index) selectPreset(ids[next])
    }
    fun beginAmount() {
        if (state.value.edit.preset == null || state.value.loading || state.value.exporting || state.value.editingAmount) return
        amountStart = state.value.edit
        state.update { it.copy(editingAmount = true) }
    }
    fun previewAmount(amount: Float) = change(state.value.edit.copy(amount = amount.coerceIn(0f, 200f)), false)
    fun finishAmount(confirm: Boolean) {
        val start = amountStart ?: return
        amountStart = null
        if (!confirm) change(start, false)
        else if (start != state.value.edit) {
            if (history.size >= 40) history.removeFirst()
            history.addLast(start)
        }
        state.update { it.copy(editingAmount = false, canUndo = history.isNotEmpty()) }
        requestPreview()
    }
    fun selectTab(tab: String) { state.update { it.copy(tab = tab) }; requestPreview() }
    fun selectGroup(group: String) { state.update { it.copy(group = group) } }
    fun toggleFavorite(id: String) {
        val favorites = state.value.favorites.toMutableSet().apply { if (!add(id)) remove(id) }.toSet()
        preferences.edit { putStringSet("favorites", favorites) }
        state.update { it.copy(favorites = favorites) }
    }
    fun dismissMessage() { state.update { it.copy(message = null) } }

    private fun requestPreview() {
        if (!state.value.hasPhoto) return
        val generation = ++revision
        previewJob?.cancel()
        state.update { it.copy(rendering = true) }
        previewJob = viewModelScope.launch {
            delay(100)
            val snapshot = state.value
            val edit = if (snapshot.tab == "Recortar") snapshot.edit.copy(crop = CropRect()) else snapshot.edit
            try {
                val bitmap = renderBitmap(edit, 1440)
                ensureActive()
                if (generation == revision) state.update { it.copy(preview = bitmap.bitmap, effectiveControls = bitmap.controls) }
                // Amount previews are provisional until ✓; a process restart must
                // recover the last committed edit, never an unconfirmed slider value.
                if (!snapshot.editingAmount) saveDraft(snapshot.edit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e) }
            finally { if (generation == revision) state.update { it.copy(rendering = false) } }
        }
    }

    private fun refreshThumbnails() {
        if (!state.value.hasPhoto) return
        thumbnailJob?.cancel()
        thumbnailJob = viewModelScope.launch {
            for (preset in state.value.presets) {
                if (state.value.thumbnails.containsKey(preset.id)) continue
                try {
                    val bitmap = renderBitmap(EditState(preset = preset.id), 160)
                    ensureActive()
                    state.update { it.copy(thumbnails = it.thumbnails + (preset.id to bitmap.bitmap)) }
                    yield()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* A missing thumbnail doesn't prevent applying a preset. */ }
            }
        }
    }

    private suspend fun saveDraft(edit: EditState) = withContext(Dispatchers.IO) {
        val file = source ?: return@withContext
        val temp = File(app.filesDir, "draft.tmp")
        temp.writeText(JSONObject().put("source", file.absolutePath).put("name", state.value.photoName).put("edit", edit.json()).toString())
        check(temp.renameTo(draft)) { "Não foi possível salvar a edição" }
    }

    fun exportPhoto() = viewModelScope.launch {
        state.update { it.copy(exporting = true) }
        val file = File(app.cacheDir, "export-${UUID.randomUUID()}.jpg")
        var uri: Uri? = null
        try {
            val edit = state.value.edit
            call(JSONObject().put("method", "render").put("edit", edit.json()).put("edge", 4096).put("path", file.absolutePath))
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Lightcraft-${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lightcraft")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                uri = checkNotNull(app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                app.contentResolver.openOutputStream(uri!!)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Não foi possível salvar a foto")
                app.contentResolver.update(uri!!, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            state.update { it.copy(message = "Foto salva em Pictures/Lightcraft") }
        } catch (e: Exception) {
            uri?.let { pending -> withContext(Dispatchers.IO) { app.contentResolver.delete(pending, null, null) } }
            error(e)
        } finally { withContext(NonCancellable + Dispatchers.IO) { file.delete() }; state.update { it.copy(exporting = false) } }
    }

    private fun error(e: Exception) {
        if (e is CancellationException) throw e
        state.update { it.copy(loading = false, rendering = false, message = e.message ?: "Não foi possível concluir a operação") }
    }
}
