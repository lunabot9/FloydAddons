package gg.floyd.utils

import java.nio.file.Files
import java.nio.file.Path

fun openDirectory(path: Path): Boolean {
    Files.createDirectories(path)
    return runCatching {
        ProcessBuilder(FloydPlatform.fileManagerCommand(path.toString())).start()
        true
    }.getOrDefault(false)
}
