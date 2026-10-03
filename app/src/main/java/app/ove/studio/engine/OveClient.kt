package app.ove.studio.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors

/**
 * The client of the OVE engine. Single-writer discipline: every call is
 * serialized onto ONE engine dispatcher thread, matching the engine's
 * single-writer v1 model (docs/ENGINE_INTEGRATION_AUDIT.md §9).
 */
class OveClient private constructor(
    private val engineDispatcher: CoroutineDispatcher,
    private val json: Json,
) {
    constructor() : this(
        engineDispatcher = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ove-engine").apply { priority = Thread.NORM_PRIORITY }
        }.asCoroutineDispatcher(),
        json = Json { ignoreUnknownKeys = true; isLenient = false },
    )

    private suspend fun <T> engine(block: () -> T): T = withContext(engineDispatcher) { block() }

    private fun decode(raw: String): OveResult =
        try {
            json.decodeFromString<OveResult>(raw)
        } catch (e: Exception) {
            throw OveException(OveError("EngineInternal", "bridge response unreadable: ${e.message}"))
        }

    /** Every JNI entry is guarded: a native-layer Throwable (UnsatisfiedLink-
     *  Error from a 16KB-page device loading a 4KB-aligned lib, SIGSEGV-adjacent
     *  loader errors, OOM) becomes a TYPED error envelope instead of a crash
     *  with no surface. v0.1.2 lesson — failures must be visible and named. */
    private suspend fun call(block: () -> String): OveResult = engine {
        try {
            decode(block())
        } catch (e: OveException) {
            throw e
        } catch (e: Throwable) {
            OveResult(
                ok = false,
                kind = "BridgeLoadError",
                message = "native engine bridge failed: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}. " +
                    "If this persists, install the latest release (16KB page-size fix).",
            )
        }
    }

    suspend fun version(): OveResult = call { OveJni.nativeVersion() }

    /** Renders one real engine-composited frame into [buffer] (RGBA, w*h*4). */
    suspend fun renderFrame(t: RationalValue, w: Int, h: Int, buffer: ByteArray): OveResult =
        call { OveJni.nativeRenderFrame(t.num, t.den, w, h, buffer) }

    suspend fun createProject(dir: String, tickAxis: RationalValue): OveResult =
        call { OveJni.nativeCreateProject(dir, tickAxis.num, tickAxis.den) }

    suspend fun openProject(dir: String): OveResult = call { OveJni.nativeOpenProject(dir) }
    suspend fun closeProject(): OveResult = call { OveJni.nativeCloseProject() }
    suspend fun importMedia(path: String): OveResult = call { OveJni.nativeImportMedia(path) }
    suspend fun addTrack(id: Long): OveResult = call { OveJni.nativeAddTrack(id) }

    suspend fun addClip(
        track: Long, hash: String, duration: RationalValue, sourceIn: RationalValue,
    ): OveResult = call {
        OveJni.nativeAddClip(track, hash, duration.num, duration.den, sourceIn.num, sourceIn.den)
    }

    suspend fun split(track: Long, clip: Long, at: RationalValue): OveResult =
        call { OveJni.nativeSplit(track, clip, at.num, at.den) }

    suspend fun resize(track: Long, clip: Long, duration: RationalValue): OveResult =
        call { OveJni.nativeResize(track, clip, duration.num, duration.den) }

    suspend fun moveClip(clip: Long, from: Long, to: Long, index: Long): OveResult =
        call { OveJni.nativeMoveClip(clip, from, to, index) }

    suspend fun removeClip(track: Long, clip: Long): OveResult =
        call { OveJni.nativeRemoveClip(track, clip) }

    suspend fun undo(): OveResult = call { OveJni.nativeUndo() }
    suspend fun redo(): OveResult = call { OveJni.nativeRedo() }

    suspend fun exportComposite(out: String, rate: RationalValue): OveResult =
        call { OveJni.nativeExportReencode(out, rate.num, rate.den) }

    suspend fun exportSegment(
        out: String, hash: String, start: RationalValue, end: RationalValue,
    ): OveResult = call {
        OveJni.nativeExportCopy(out, hash, start.num, start.den, end.num, end.den)
    }

    suspend fun exportWav(out: String): OveResult = call { OveJni.nativeExportWav(out) }
}
