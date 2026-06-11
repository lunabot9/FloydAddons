package gg.floyd.utils

/**
 * The single source of truth for host-OS/arch detection — every Floyd feature that behaves
 * differently per platform (dock vs window icons, Discord IPC transport, file-manager commands,
 * windowing quirks) branches on this object instead of re-parsing `os.name` ad hoc, so the
 * platform matrix lives in ONE place and a new OS-specific fix is grep-able from here.
 *
 * Pure JVM (no MC/GL/native deps) and computed once at class init: `os.name`/`os.arch` are
 * immutable for the process lifetime.
 */
object FloydPlatform {

    enum class OS { WINDOWS, MACOS, LINUX }

    @JvmStatic
    val os: OS = System.getProperty("os.name", "").lowercase().let { name ->
        when {
            name.contains("win") -> OS.WINDOWS
            name.contains("mac") || name.contains("darwin") -> OS.MACOS
            // Everything else (Linux, BSDs, unknowns) takes the POSIX/X11 defaults.
            else -> OS.LINUX
        }
    }

    @JvmStatic
    val isWindows: Boolean get() = os == OS.WINDOWS

    @JvmStatic
    val isMac: Boolean get() = os == OS.MACOS

    @JvmStatic
    val isLinux: Boolean get() = os == OS.LINUX

    /** True on arm64/aarch64 JVMs (e.g. Apple Silicon — where the discord-rpc JNI has no binary). */
    @JvmStatic
    val isArm64: Boolean = System.getProperty("os.arch", "").lowercase() in setOf("aarch64", "arm64")

    /** The OS file-manager command that opens/reveals [path] (explorer / open / xdg-open). */
    @JvmStatic
    fun fileManagerCommand(path: String): List<String> = when (os) {
        OS.WINDOWS -> listOf("explorer", path)
        OS.MACOS -> listOf("open", path)
        OS.LINUX -> listOf("xdg-open", path)
    }
}
