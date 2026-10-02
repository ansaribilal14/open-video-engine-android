package app.ove.studio.engine

/**
 * JNI boundary over `liboveandroid.so` — the ove-android bridge.
 * Every method returns the raw JSON envelope string; [OveClient] parses and
 * types it. All times are exact rationals (num, den) — never floats.
 */
internal object OveJni {
    init {
        System.loadLibrary("oveandroid")
    }

    external fun nativeVersion(): String
    external fun nativeCreateProject(dir: String, tickNum: Long, tickDen: Long): String
    external fun nativeOpenProject(dir: String): String
    external fun nativeCloseProject(): String
    external fun nativeImportMedia(path: String): String
    external fun nativeAddTrack(id: Long): String
    external fun nativeAddClip(
        track: Long, hash: String,
        durNum: Long, durDen: Long, inNum: Long, inDen: Long,
    ): String
    external fun nativeSplit(track: Long, clip: Long, atNum: Long, atDen: Long): String
    external fun nativeResize(track: Long, clip: Long, durNum: Long, durDen: Long): String
    external fun nativeMoveClip(clip: Long, from: Long, to: Long, index: Long): String
    external fun nativeRemoveClip(track: Long, clip: Long): String
    external fun nativeUndo(): String
    external fun nativeRedo(): String
    external fun nativeExportReencode(out: String, rateNum: Long, rateDen: Long): String
    external fun nativeExportCopy(
        out: String, hash: String,
        sNum: Long, sDen: Long, eNum: Long, eDen: Long,
    ): String
    external fun nativeExportWav(out: String): String
    external fun nativeRenderFrame(
        tNum: Long, tDen: Long, w: Int, h: Int, out: ByteArray,
    ): String
}
