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
 *
 * v0.1.2 ordering fix ("Engine folder missing" dead-end): the registry never
 * creates or deletes the project folder itself — the ENGINE creates it in
 * Project::create (which asserts absence). Callers reserve a name with
 * [nextDirName], run the engine create, and only then persist the row with
 * [register]. A registry row therefore always corresponds to an engine
 * project that existed at save time.
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

    /** Reserve a unique, filesystem-safe project folder name (no side effects). */
    fun nextDirName(name: String): String {
        val base = name.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").ifEmpty { "project" }
        var candidate = "$base.ove"
        var i = 2
        while (list().any { it.dirName == candidate }) {
            candidate = "$base-$i.ove"
            i++
        }
        return candidate
    }

    /** The folder the engine will create for [dirName] (may not exist yet). */
    fun dirForDirName(dirName: String): File = File(root, dirName)

    /** Persist the row AFTER the engine project was created successfully. */
    fun register(name: String, dirName: String): ProjectEntry {
        val entry = ProjectEntry(
            dirName = dirName,
            name = name,
            createdAtMs = System.currentTimeMillis(),
            lastOpenedMs = System.currentTimeMillis(),
        )
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

    private fun save(entry: ProjectEntry) {
        val all = list().toMutableList()
        all.removeAll { it.dirName == entry.dirName }
        all.add(entry)
        indexFile.writeText(json.encodeToString<List<ProjectEntry>>(all))
    }
}
