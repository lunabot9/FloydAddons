package gg.floyd.utils.font

import com.mojang.logging.LogUtils
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FreeType
import org.lwjgl.util.msdfgen.MSDFGen
import org.lwjgl.util.msdfgen.MSDFGenExt
import org.lwjgl.util.msdfgen.MSDFGenFTLoadCallback

/**
 * Lazy probe + process-global handle for the lwjgl-msdfgen binding. FreeType symbols are resolved
 * from Minecraft's own lwjgl-freetype shared library via the msdfgen load callback (set exactly
 * once, before [MSDFGenExt.msdf_ft_init]; the callback closure is intentionally never freed).
 * Everything is CPU-only — safe on the FontManager prepare executor, never touches GL. Link
 * failures are [Error]s, so the probe catches [Throwable] and reports unavailability instead of
 * escaping `FontManager.safeLoad`.
 */
object MsdfNative {
    private val logger = LogUtils.getLogger()

    @Volatile
    private var probed = false

    @Volatile
    private var available = false

    @Volatile
    private var status = "unprobed"

    private var ftHandle = 0L

    // Process-global upcall closure: msdfgen keeps the native pointer forever, so the Java-side
    // callback object must stay strongly referenced and must never be freed.
    @Suppress("unused")
    private var loadCallback: MSDFGenFTLoadCallback? = null

    @JvmStatic
    @Synchronized
    fun probe(): Boolean {
        if (probed) return available
        probed = true
        try {
            val freetype = FreeType.getLibrary()
            val callback = MSDFGenFTLoadCallback.create { name ->
                freetype.getFunctionAddress(MemoryUtil.memUTF8(name))
            }
            val callbackResult = MSDFGenExt.msdf_ft_set_load_callback(callback)
            if (callbackResult != MSDFGen.MSDF_SUCCESS) {
                throw IllegalStateException("msdf_ft_set_load_callback failed: error $callbackResult")
            }
            loadCallback = callback
            MemoryStack.stackPush().use { stack ->
                val handle = stack.mallocPointer(1)
                val initResult = MSDFGenExt.msdf_ft_init(handle)
                if (initResult != MSDFGen.MSDF_SUCCESS) {
                    throw IllegalStateException("msdf_ft_init failed: error $initResult")
                }
                ftHandle = handle.get(0)
            }
            available = true
            status = "ok: msdfgen ft handle=0x${java.lang.Long.toHexString(ftHandle)}, freetype=${freetype.path}"
            logger.info("[FloydMSDF] native probe OK ({})", status)
        } catch (t: Throwable) {
            available = false
            status = "failed: ${t.javaClass.name}: ${t.message}"
            logger.warn("[FloydMSDF] native probe failed, MSDF font disabled for this session", t)
        }
        return available
    }

    @JvmStatic
    fun statusString(): String = status

    fun handle(): Long {
        check(available && ftHandle != 0L) { "msdfgen natives unavailable: $status" }
        return ftHandle
    }
}
