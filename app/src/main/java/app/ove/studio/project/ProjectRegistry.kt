package app.ove.studio.project

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The client-side project registry. The engine owns the PROJECT (folder,
 * manifest, log); the registry only remembers which folders the user created
 * and their display metadata (the engine has no project-listing API —
 * INTEGRATION_GAPS #9, client-side by design).
 */
@Serializable
data class ProjectEntry(
    val dirName: String,
    val name: String,
    val createdAtMs: Long,
    val lastOpenedMs: Long,
)

class ProjectRegistry(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val root: File = File(context.filesDir, "projects").apply { mkdirs() }
    private val indexFile: File = File(root, "registry.json")

    val projectsDir: File get() = root

    fun list(): List<ProjectEntry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ProjectEntry>>(indexFile.readText())
        }.getOrDefault(emptyList()).sortedByDescending { it.lastOpenedMs }
    }

    fun create(name: String): ProjectEntry {
        val dirName = uniqueDirName(name)
        val entry = ProjectEntry(
            dirName = dirName,
            name = name,
            createdAtMs = System.currentTimeMillis(),
            lastOpenedMs = System.currentTimeMillis(),
        )
        File(root, dirName).mkdirs() // engine will create the project inside its own subdir? No:
        // the engine project IS this folder — Engine::create asserts absence,
        // so the folder must not exist. Keep registry bookkeeping only:
        File(root, dirName).delete()
        save(entry)
        return entry
    }

    fun touch(entry: ProjectEntry) {
        save(entry.copy(lastOpenedMs = System.currentTimeMillis()))
    }

    fun remove(entry: ProjectEntry) {
        val all = list().toMutableList()
        all.removeAll { it.dirName == entry.dirName }
        indexFile.writeText(json.encodeToString<List<ProjectEntry>>(all))
        File(root, entry.dirName).deleteRecursively()
    }

    fun dirFor(entry: ProjectEntry): File = File(root, entry.dirName)

    fun storageBytes(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun uniqueDirName(name: String): String {
        val base = name.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").ifEmpty { "project" }
        var candidate = "$base.ove"
        var i = 2
        while (list().any { it.dirName == candidate }) {
            candidate = "$base-$i.ove"
            i++
        }
        return candidate
    }

    private fun save(entry: ProjectEntry) {
        val all = list().toMutableList()
        all.removeAll { it.dirName == entry.dirName }
        all.add(entry)
        indexFile.writeText(json.encodeToString<List<ProjectEntry>>(all))
    }
}
