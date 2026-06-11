package gg.floyd.utils

import java.nio.file.Files
import java.nio.file.Path

fun openDirectory(path: Path): Boolean = runCatching {
    Files.createDirectories(path)
    ProcessBuilder(FloydPlatform.fileManagerCommand(path.toString())).start()
    true
}.getOrDefault(false)
