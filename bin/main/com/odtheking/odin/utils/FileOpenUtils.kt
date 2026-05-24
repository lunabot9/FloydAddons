package com.odtheking.odin.utils

import java.nio.file.Files
import java.nio.file.Path

fun openDirectory(path: Path): Boolean {
    Files.createDirectories(path)
    val command = when {
        System.getProperty("os.name", "").contains("win", ignoreCase = true) -> listOf("explorer", path.toString())
        System.getProperty("os.name", "").contains("mac", ignoreCase = true) -> listOf("open", path.toString())
        else -> listOf("xdg-open", path.toString())
    }
    return runCatching {
        ProcessBuilder(command).start()
        true
    }.getOrDefault(false)
}
