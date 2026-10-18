package app.filmcraft.mobile

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.filmcraft.mobile.core.ClipView
import app.filmcraft.mobile.core.CoreResult
import app.filmcraft.mobile.core.NativeCore
import app.filmcraft.mobile.core.Ops
import app.filmcraft.mobile.core.ProjectView
import app.filmcraft.mobile.export.Exporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val uri: String) : ExportState
    data class Failed(val message: String) : ExportState
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    var view by mutableStateOf(ProjectView.EMPTY)
        private set
    var selectedId by mutableStateOf<Long?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var export by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    /** Miniaturas por "idDoClipe:entradaMs". */
    val thumbs = mutableStateMapOf<String, Bitmap>()

    /** Estado do projeto no formato do núcleo Rust (JSON). */
    private var stateJson: String = ""

    val selected: ClipView?
        get() = view.clips.firstOrNull { it.id == selectedId }

    init {
        if (NativeCore.loaded) {
            handle(NativeCore.create())
        } else {
            message = "Não foi possível carregar o núcleo nativo (libfilmcraft_mobile.so)."
        }
    }

    private fun handle(result: CoreResult): Boolean = when (result) {
        is CoreResult.Ok -> {
            stateJson = result.stateJson
            view = result.view
            if (view.clips.none { it.id == selectedId }) selectedId = view.clips.firstOrNull()?.id
            true
        }
        is CoreResult.Err -> {
            message = result.message
            false
        }
    }

    private fun run(op: JSONObject): Boolean = NativeCore.loaded && handle(NativeCore.apply(stateJson, op))

    fun dismissMessage() {
        message = null
    }

    fun select(id: Long) {
        selectedId = id
    }

    fun addVideos(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val probed = withContext(Dispatchers.IO) { MediaProbe.probe(getApplication(), uri) }
                if (probed == null) {
                    message = "Não consegui ler este vídeo."
                    continue
                }
                if (run(Ops.addClip(uri.toString(), probed.name, probed.durationMs))) {
                    selectedId = view.clips.lastOrNull()?.id
                }
            }
        }
    }

    fun removeSelected() {
        selectedId?.let { run(Ops.remove(it)) }
    }

    fun duplicateSelected() {
        selectedId?.let { id ->
            if (run(Ops.duplicate(id))) {
                val i = view.clips.indexOfFirst { it.id == id }
                selectedId = view.clips.getOrNull(i + 1)?.id ?: id
            }
        }
    }

    fun toggleMuteSelected() {
        selectedId?.let { run(Ops.toggleMute(it)) }
    }

    fun moveSelected(delta: Int) {
        val id = selectedId ?: return
        val i = view.clips.indexOfFirst { it.id == id }
        if (i < 0) return
        val to = (i + delta).coerceIn(0, view.clips.lastIndex)
        if (to != i) run(Ops.move(id, to))
    }

    fun trimSelected(inMs: Long, outMs: Long) {
        selectedId?.let { run(Ops.trim(it, inMs, outMs)) }
    }

    fun splitAt(timelineMs: Long) {
        if (run(Ops.split(timelineMs))) {
            // seleciona a metade da direita
            val sel = selectedId
            val i = view.clips.indexOfFirst { it.id == sel }
            view.clips.getOrNull(i + 1)?.let { selectedId = it.id }
        }
    }

    suspend fun loadThumbnail(clip: ClipView) {
        val key = "${clip.id}:${clip.inMs}"
        if (thumbs.containsKey(key)) return
        val bmp = withContext(Dispatchers.IO) { MediaProbe.thumbnail(getApplication(), Uri.parse(clip.uri), clip.inMs) }
        if (bmp != null) thumbs[key] = bmp
    }

    fun exportVideo() {
        if (view.clips.isEmpty() || export is ExportState.Running) return
        export = ExportState.Running(0f)
        val clips = view.clips
        viewModelScope.launch {
            try {
                val uri = Exporter.export(getApplication(), clips) { p -> export = ExportState.Running(p) }
                export = ExportState.Done(uri.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                export = ExportState.Failed(e.message ?: "Falha na exportação")
            }
        }
    }

    fun dismissExport() {
        if (export !is ExportState.Running) export = ExportState.Idle
    }
}
