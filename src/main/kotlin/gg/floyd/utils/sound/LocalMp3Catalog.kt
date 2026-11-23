package gg.floyd.utils.sound

import java.nio.file.Files
import java.nio.file.Path

class LocalMp3Catalog(val directory: Path) {
    fun choices(): List<String> {
        Files.createDirectories(directory)
        return listOf(DEFAULT) + Files.list(directory).use { files ->
            files.filter { Files.isRegularFile(it) && Files.isReadable(it) && it.fileName.toString().endsWith(".mp3", true) }
                .map { it.fileName.toString() }.sorted(String.CASE_INSENSITIVE_ORDER).toList()
        }
    }

    fun resolve(name: String): Path? {
        if (name == DEFAULT || name.isBlank() || Path.of(name).fileName.toString() != name) return null
        val path = directory.resolve(name).normalize()
        return path.takeIf { it.startsWith(directory.normalize()) && it.fileName.toString().endsWith(".mp3", true) && Files.isRegularFile(it) && Files.isReadable(it) }
    }

    fun cycle(current: String, direction: Int): String {
        val choices = choices()
        val index = choices.indexOf(current).coerceAtLeast(0)
        return choices[Math.floorMod(index + direction, choices.size)]
    }

    companion object { const val DEFAULT = "Default" }
}
