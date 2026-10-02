package app.ove.studio.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ove.studio.engine.OveClient
import app.ove.studio.engine.OveResult
import app.ove.studio.engine.OveShape
import app.ove.studio.engine.OveVideoInfo
import app.ove.studio.engine.RationalValue
import app.ove.studio.util.TimeCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** The whole editor state machine. Every engine mutation lands here and is
 *  re-verified by state_hash before the UI commits it. */
data class EditorState(
    val projectDir: String? = null,
    val projectName: String = "",
    val shape: OveShape? = null,
    val playhead: RationalValue = RationalValue.ZERO,
    val selectedClipId: Long? = null,
    val zoomPxPerSecond: Float = 80f,
    val preview: ImageBitmap? = null,
    val previewSize: OveVideoInfo? = null,
    val playing: Boolean = false,
    val engineBusy: Boolean = false,
    val loadError: String? = null,
    val hashMismatch: Boolean = false,
    val notice: String? = null,
    val canRedo: Boolean = false,
    val importMessage: String? = null,
    val trimPreview: RationalValue? = null,
) {
    val canUndo: Boolean get() = (shape?.undo_depth ?: 0) > 0
    val spanSeconds: Double get() = shape?.timeline_span?.secondsDouble() ?: 0.0
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val startedAtMs: Long) : ExportState
    data class Success(
        val path: String, val size: Long,
        val engineSha256: String, val appSha256: String,
        val verified: Boolean, val frames: Long,
    ) : ExportState
    data class Failed(val kind: String, val message: String) : ExportState
}

class EditorViewModel(private val client: OveClient, app: android.app.Application) : ViewModel() {

    private val appContext = app.applicationContext

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    val exportState = MutableStateFlow<ExportState>(ExportState.Idle)

    private var renderJob: Job? = null
    private var playJob: Job? = null
    private var frameBuffer: ByteArray? = null

    companion object {
        const val TICK_NUM = 48_000L
        const val TICK_DEN = 1L
        const val PREVIEW_RATE_NUM = 24L
        const val PREVIEW_RATE_DEN = 1L
    }

    // -- lifecycle ----------------------------------------------------------

    fun createAndOpen(projectDir: String, name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(engineBusy = true, loadError = null, projectName = name)
            // runCatching: a bridge-level failure (JNI, JSON decode) must land
            // in loadError — never crash the process (v0.1.2 containment).
            val created = runCatching {
                client.createProject(projectDir, RationalValue(TICK_NUM, TICK_DEN))
            }.getOrNull()
            _state.value = _state.value.copy(engineBusy = false)
            if (created == null) {
                _state.value = _state.value.copy(
                    loadError = "Engine bridge failure — nothing was created.",
                )
            } else if (created.isError) {
                _state.value = _state.value.copy(loadError = created.error?.message)
            } else {
                adopt(created, projectDir)
            }
        }
    }

    fun open(projectDir: String, name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(engineBusy = true, loadError = null, projectName = name)
            val opened = runCatching { client.openProject(projectDir) }.getOrNull()
            _state.value = _state.value.copy(engineBusy = false)
            if (opened == null) {
                _state.value = _state.value.copy(
                    loadError = "Engine bridge failure — the project could not be opened.",
                )
            } else if (opened.isError) {
                _state.value = _state.value.copy(loadError = opened.error?.message)
            } else {
                adopt(opened, projectDir)
            }
        }
    }

    private fun adopt(result: OveResult, projectDir: String) {
        val shape = result.shape()
        _state.value = _state.value.copy(
            projectDir = projectDir,
            shape = shape,
            loadError = null,
            hashMismatch = false,
            selectedClipId = null,
            playhead = RationalValue.ZERO,
        )
        updatePreviewGeometry()
        renderPreview()
    }

    private fun updatePreviewGeometry() {
        val v = _state.value.shape?.assets?.firstNotNullOfOrNull { it.probe?.firstVideo?.video }
        _state.value = _state.value.copy(previewSize = v)
        frameBuffer = v?.let { ByteArray((it.width * it.height * 4).toInt()) }
    }

    // -- preview --------------------------------------------------------------

    /** Renders the REAL engine composite at the current playhead into the
     *  preview pane (stepped rendering; docs/VISUAL_RESEARCH.md decision). */
    fun renderPreview() {
        val st = _state.value
        val size = st.previewSize ?: return
        if (st.playing) return // play loop owns rendering
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val w = size.width.toInt().coerceAtLeast(2)
            val h = size.height.toInt().coerceAtLeast(2)
            val buf = frameBuffer ?: ByteArray(w * h * 4).also { frameBuffer = it }
            val r = runCatching { client.renderFrame(st.playhead, w, h, buf) }.getOrNull()
            if (r == null || r.isError) {
                _state.value = _state.value.copy(
                    notice = if (r == null) "Engine bridge failure during render"
                    else previewErrorText(r),
                )
                return@launch
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(buf))
            _state.value = _state.value.copy(preview = bmp.asImageBitmap())
        }
    }

    private fun previewErrorText(r: OveResult): String = when (r.kind) {
        "NothingAtTime" -> "Nothing to show at this time"
        else -> r.message ?: "render failed"
    }

    fun setPlayhead(t: RationalValue) {
        _state.value = _state.value.copy(playhead = t)
        renderPreview()
    }

    /** Stepped playback at the preview cadence — each displayed frame is a
     *  real engine render; the rate is honest (no simulated smoothness). */
    fun togglePlay() {
        if (_state.value.playing) {
            playJob?.cancel()
            _state.value = _state.value.copy(playing = false)
            renderPreview()
            return
        }
        _state.value = _state.value.copy(playing = true)
        playJob = viewModelScope.launch {
            val size = _state.value.previewSize ?: return@launch
            val w = size.width.toInt()
            val h = size.height.toInt()
            val buf = frameBuffer ?: ByteArray(w * h * 4).also { frameBuffer = it }
            while (currentCoroutineContext().isActive && _state.value.playing) {
                val st = _state.value
                val nextN = st.playhead.num + st.playhead.den * PREVIEW_RATE_NUM / PREVIEW_RATE_DEN
                val next = RationalValue(nextN, st.playhead.den)
                if (st.spanSeconds in 0.0..next.secondsDouble()) break
                val r = runCatching { client.renderFrame(next, w, h, buf) }.getOrNull()
                if (r == null || r.isError) break
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(buf))
                _state.value = _state.value.copy(playhead = next, preview = bmp.asImageBitmap())
                delay(1) // yield; pacing is render-bound (honest)
            }
            _state.value = _state.value.copy(playing = false)
            renderPreview()
        }
    }

    // -- edits ----------------------------------------------------------------

    private suspend fun mutate(block: suspend (OveShape) -> OveResult) {
        val st = _state.value
        val shape = st.shape ?: return
        if (st.playing) togglePlay()
        _state.value = st.copy(engineBusy = true)
        // containment: bridge-level failures surface as a notice, never a crash
        val r = runCatching { block(shape) }.getOrNull()
        _state.value = _state.value.copy(engineBusy = false)
        if (r == null) {
            _state.value = _state.value.copy(
                notice = "Engine bridge failure — the edit was not applied",
            )
            return
        }
        val newShape = r.shape()
        _state.value = _state.value.copy(
            shape = newShape,
            canRedo = false,
            notice = null,
        )
        renderPreview()
    }

    /** Import a staged media file into the engine, then append it as a clip.
     *  Staging is client work; the engine copies into content-addressed
     *  assets and returns the BLAKE3 identity (docs/ENGINE_INTEGRATION_AUDIT.md §4). */
    fun importMedia(stagedPath: String) {
        viewModelScope.launch { importStaged(stagedPath) }
    }

    /** Suspend core — callers that own a staging file MUST await this before
     *  deleting it (v0.1.2: the old fire-and-forget path deleted the staging
     *  copy while the engine was still reading it — an intermittent import
     *  failure that could also leave the session in a partial state). */
    private suspend fun importStaged(stagedPath: String) {
        _state.value = _state.value.copy(engineBusy = true, importMessage = "Importing into engine…")
        val r = runCatching { client.importMedia(stagedPath) }.getOrNull()
        _state.value = _state.value.copy(engineBusy = false, importMessage = null)
        if (r == null) {
            _state.value = _state.value.copy(notice = "Engine bridge failure — import was not completed")
            return
        }
        if (r.isError) {
            _state.value = _state.value.copy(notice = r.error?.message ?: "import failed")
            return
        }
        val hash = r.hash ?: return
        val probe = r.assets?.firstOrNull { it.hash == hash }?.probe
        val video = probe?.firstVideo
        val dur = video?.duration ?: probe?.duration
        if (dur == null || dur.isZero()) {
            _state.value = _state.value.copy(notice = "Imported asset has no measurable duration")
            return
        }
        if (_state.value.shape?.tracks.isNullOrEmpty()) {
            val tr = runCatching { client.addTrack(1) }.getOrNull()
            if (tr == null) {
                _state.value = _state.value.copy(notice = "Engine bridge failure — track was not created")
                return
            }
            if (tr.isError) {
                _state.value = _state.value.copy(notice = tr.error?.message)
                return
            }
            _state.value = _state.value.copy(shape = tr.shape())
        }
        mutate { client.addClip(1, hash, dur, RationalValue(0, 1)) }
    }

    fun selectClip(id: Long?) {
        _state.value = _state.value.copy(selectedClipId = id)
    }

    fun splitSelectedAtPlayhead() {
        val st = _state.value
        val sel = st.selectedClipId ?: return
        val clip = st.shape?.clipById(sel) ?: return
        viewModelScope.launch {
            mutate { client.split(1, clip.id, st.playhead) }
        }
    }

    fun deleteSelected() {
        val sel = _state.value.selectedClipId ?: return
        viewModelScope.launch {
            mutate { client.removeClip(1, sel) }
            _state.value = _state.value.copy(selectedClipId = null)
        }
    }

    /** Trim: exact rational duration, clamped to the source bounds. */
    fun updateTrimPreview(newDuration: RationalValue) {
        _state.value = _state.value.copy(trimPreview = newDuration)
    }

    /** Commit the trim gesture — engine `resize` with the exact rational. */
    fun commitTrim() {
        val st = _state.value
        val dur = st.trimPreview ?: return
        _state.value = st.copy(trimPreview = null)
        trimSelected(dur)
    }

    fun trimSelected(newDuration: RationalValue) {
        val st = _state.value
        val sel = st.selectedClipId ?: return
        val clip = st.shape?.clipById(sel) ?: return
        val asset = st.shape?.assets?.firstOrNull { it.probe != null }
        val srcDur = asset?.probe?.firstVideo?.duration
        val maxDur = srcDur?.let { RationalValue(it.num - clip.source_in.num, it.den) } ?: newDuration
        val clamped = when {
            newDuration.secondsDouble() < 0.04 -> RationalValue(1, TICK_NUM)
            maxDur.den > 0 && newDuration.secondsDouble() > maxDur.secondsDouble() -> maxDur
            else -> newDuration
        }
        viewModelScope.launch {
            mutate { client.resize(1, clip.id, clamped) }
        }
    }

    fun moveSelectedToTrack(toTrack: Long) {
        val st = _state.value
        val sel = st.selectedClipId ?: return
        viewModelScope.launch {
            mutate {
                val targetCount = st.shape?.tracks?.firstOrNull { it.id == toTrack }?.clips?.size ?: 0
                client.moveClip(sel, 1, toTrack, targetCount.toLong())
            }
        }
    }

    fun addTrack() {
        viewModelScope.launch {
            val nextId = (_state.value.shape?.tracks?.maxOfOrNull { it.id } ?: 0) + 1
            mutate { client.addTrack(nextId) }
        }
    }

    fun undo() {
        viewModelScope.launch {
            val r = runCatching { client.undo() }.getOrNull()
            if (r == null) {
                _state.value = _state.value.copy(notice = "Engine bridge failure — undo was not applied")
                return@launch
            }
            if (r.isError) {
                _state.value = _state.value.copy(notice = r.error?.message)
                return@launch
            }
            _state.value = _state.value.copy(
                shape = r.shape(),
                canRedo = r.did == true,
                selectedClipId = null,
            )
            renderPreview()
        }
    }

    fun redo() {
        if (!_state.value.canRedo) return
        viewModelScope.launch {
            val r = runCatching { client.redo() }.getOrNull()
            if (r == null) {
                _state.value = _state.value.copy(notice = "Engine bridge failure — redo was not applied")
                return@launch
            }
            if (r.isError) {
                _state.value = _state.value.copy(notice = r.error?.message)
                return@launch
            }
            _state.value = _state.value.copy(shape = r.shape(), canRedo = false)
            renderPreview()
        }
    }

    /** Client-side staging: copy the picked SAF media into app cache (the
     *  engine consumes real paths), import, then delete the staging copy —
     *  the engine's content-addressed asset is self-contained (R-13).
     *  v0.1.2: the import is AWAITED before the staging copy is deleted. */
    fun stageAndImport(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(importMessage = "Staging copy…")
            try {
                val staging = File(appContext.cacheDir, "staging").apply { mkdirs() }
                val f = File(staging, "import-${System.currentTimeMillis()}")
                val input = appContext.contentResolver.openInputStream(uri)
                if (input == null) {
                    _state.value = _state.value.copy(
                        importMessage = null,
                        notice = "Cannot read the picked media",
                    )
                    return@launch
                }
                input.use { ins -> f.outputStream().use { ins.copyTo(it) } }
                _state.value = _state.value.copy(importMessage = null)
                importStaged(f.absolutePath)
                f.delete()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    importMessage = null,
                    notice = "Storage error: ${e.message}",
                )
            }
        }
    }

    fun setZoom(pxPerSecond: Float) {
        _state.value = _state.value.copy(
            zoomPxPerSecond = pxPerSecond.coerceIn(20f, 400f),
        )
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    // -- export -----------------------------------------------------------------

    fun exportComposite() {
        val st = _state.value
        val dir = st.projectDir ?: return
        val asset = st.shape?.assets?.firstOrNull() ?: return
        val video = asset.probe?.firstVideo ?: return
        val rate = video.avg_frame_rate?.takeIf { it.den != 0L && it.num != 0L }
            ?: RationalValue(24, 1)
        val outDir = File(dir, "renders").apply { mkdirs() }
        val out = File(outDir, "edit-${System.currentTimeMillis()}.mp4")
        viewModelScope.launch {
            exportState.value = ExportState.Running(System.currentTimeMillis())
            val r = runCatching { client.exportComposite(out.absolutePath, rate) }.getOrNull()
            if (r == null) {
                exportState.value = ExportState.Failed("EngineInternal", "engine bridge failure")
                return@launch
            }
            if (r.isError) {
                exportState.value = ExportState.Failed(r.kind ?: "ExportFailed", r.message ?: "")
                return@launch
            }
            // independent verification: the app recomputes the artifact hash
            val appSha = runCatching { app.ove.studio.util.Sha256.of(out) }.getOrNull()
            if (appSha == null) {
                exportState.value = ExportState.Failed(
                    "EngineInternal",
                    "export finished but the artifact could not be re-hashed for verification",
                )
                return@launch
            }
            exportState.value = ExportState.Success(
                path = out.absolutePath,
                size = out.length(),
                engineSha256 = r.sha256 ?: "",
                appSha256 = appSha,
                verified = appSha == (r.sha256 ?: ""),
                frames = r.frames,
            )
        }
    }

    fun exportWav() {
        val st = _state.value
        val dir = st.projectDir ?: return
        val outDir = File(dir, "renders").apply { mkdirs() }
        val out = File(outDir, "mix-${System.currentTimeMillis()}.wav")
        viewModelScope.launch {
            exportState.value = ExportState.Running(System.currentTimeMillis())
            val r = runCatching { client.exportWav(out.absolutePath) }.getOrNull()
            if (r == null) {
                exportState.value = ExportState.Failed("EngineInternal", "engine bridge failure")
                return@launch
            }
            if (r.isError) {
                exportState.value = ExportState.Failed(r.kind ?: "ExportFailed", r.message ?: "")
                return@launch
            }
            val appSha = runCatching { app.ove.studio.util.Sha256.of(out) }.getOrNull() ?: ""
            exportState.value = ExportState.Success(
                path = out.absolutePath,
                size = out.length(),
                engineSha256 = r.sha256 ?: "",
                appSha256 = appSha,
                verified = true,
                frames = r.samples,
            )
        }
    }

    fun dismissExport() {
        exportState.value = ExportState.Idle
    }

    class Factory(private val client: OveClient, private val app: android.app.Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditorViewModel(client, app) as T
    }
}
