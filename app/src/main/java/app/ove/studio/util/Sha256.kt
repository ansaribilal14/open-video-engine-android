package app.ove.studio.util

import java.io.File
import java.security.MessageDigest

/** Independent artifact verification — the app recomputes SHA-256 and
 *  compares with the engine-reported hash before claiming export success. */
object Sha256 {
    fun of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
