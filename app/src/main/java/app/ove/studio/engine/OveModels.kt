package app.ove.studio.engine

import kotlinx.serialization.Serializable

/** Exact rational (num/den) — the ONLY time representation crossing the
 *  engine boundary. Floats are never sent to the engine (P-5). */
@Serializable
data class RationalValue(val num: Long, val den: Long) {
    /** UI display only — never sent back to the engine. */
    fun secondsDouble(): Double = num.toDouble() / den.toDouble()

    fun isZero(): Boolean = num == 0L

    fun add(other: RationalValue): RationalValue {
        val n = num * other.den + other.num * den
        val d = den * other.den
        return RationalValue(n, d).normalized()
    }

    fun sub(other: RationalValue): RationalValue {
        val n = num * other.den - other.num * den
        val d = den * other.den
        return RationalValue(n, d).normalized()
    }

    private fun normalized(): RationalValue {
        if (den == 0L) return this
        var n = num
        var d = den
        if (d < 0) { n = -n; d = -d }
        val a = if (n < 0) -n else n
        var x = a
        var y = d
        while (y != 0L) { val t = x % y; x = y; y = t }
        val g = if (a == 0L) d else x
        return if (g > 1L) RationalValue(n / g, d / g) else RationalValue(n, d)
    }

    companion object {
        val ZERO = RationalValue(0, 1)
    }
}

@Serializable
data class OveVideoInfo(val width: Long, val height: Long)

@Serializable
data class OveAudioInfo(val sample_rate: Long, val channels: Long)

@Serializable
data class OveStream(
    val kind: String,
    val codec: String,
    val duration: RationalValue? = null,
    val avg_frame_rate: RationalValue? = null,
    val video: OveVideoInfo? = null,
    val audio: OveAudioInfo? = null,
)

@Serializable
data class OveProbe(
    val duration: RationalValue? = null,
    val streams: List<OveStream> = emptyList(),
) {
    val firstVideo: OveStream? get() = streams.firstOrNull { it.kind == "Video" && it.video != null }
    val hasAudio: Boolean get() = streams.any { it.kind == "Audio" }
    val audio: OveAudioInfo? get() = streams.firstOrNull { it.kind == "Audio" }?.audio
}

@Serializable
data class OveAsset(val id: String, val hash: String, val probe: OveProbe? = null)

@Serializable
data class OveClip(
    val id: Long,
    val start: RationalValue,
    val duration: RationalValue,
    val source_in: RationalValue,
)

@Serializable
data class OveTrack(val id: Long, val clips: List<OveClip> = emptyList())

/** UI projection of the live engine document; authority verified by state_hash. */
@Serializable
data class OveShape(
    val state_hash: String,
    val undo_depth: Long = 0,
    val assets: List<OveAsset> = emptyList(),
    val tracks: List<OveTrack> = emptyList(),
    val timeline_span: RationalValue = RationalValue.ZERO,
    val render_source: String? = null,
    val multi_source_limit: Boolean = false,
) {
    val totalClips: Int get() = tracks.sumOf { it.clips.size }
    fun clipById(id: Long): OveClip? = tracks.flatMap { it.clips }.firstOrNull { it.id == id }
}

/** Composite export result (engine reports the artifact SHA-256; the app
 *  re-verifies it independently before claiming success). */
@Serializable
data class OveExportInfo(
    val path: String,
    val sha256: String,
    val size: Long,
    val frames: Long = 0,
    val snaps: Long = 0,
    val samples: Long = 0,
)

/** Typed engine error kinds — docs/ENGINE_INTEGRATION_AUDIT.md §8. */
@Serializable
data class OveError(val kind: String, val message: String) {
    fun toException() = OveException(this)
}

class OveException(val error: OveError) : Exception(error.message)

/** The engine's answer envelope. */
@Serializable
data class OveResult(
    val ok: Boolean,
    val state_hash: String? = null,
    val undo_depth: Long = 0,
    val assets: List<OveAsset>? = null,
    val tracks: List<OveTrack>? = null,
    val timeline_span: RationalValue? = null,
    val render_source: String? = null,
    val multi_source_limit: Boolean = false,
    val hash: String? = null,
    val clip_id: Long? = null,
    val new_clip_id: Long? = null,
    val did: Boolean? = null,
    val kind: String? = null,
    val message: String? = null,
    val path: String? = null,
    val sha256: String? = null,
    val size: Long = 0,
    val frames: Long = 0,
    val snaps: Long = 0,
    val samples: Long = 0,
    val width: Long = 0,
    val height: Long = 0,
    val closed: Boolean = false,
    val bridge: String? = null,
    val engine_pin: String? = null,
    val engine_version: String? = null,
) {
    val isError: Boolean get() = !ok
    val error: OveError? get() = if (isError && kind != null) OveError(kind!!, message ?: "") else null

    fun requireOk(): OveResult {
        if (isError) throw OveException(OveError(kind ?: "EngineInternal", message ?: "unknown"))
        return this
    }

    fun shape(): OveShape = OveShape(
        state_hash = state_hash ?: "",
        undo_depth = undo_depth,
        assets = assets ?: emptyList(),
        tracks = tracks ?: emptyList(),
        timeline_span = timeline_span ?: RationalValue.ZERO,
        render_source = render_source,
        multi_source_limit = multi_source_limit,
    )
}
