package gg.floyd.utils.ui.rendering

import com.mojang.blaze3d.opengl.DirectStateAccess
import com.mojang.blaze3d.systems.RenderSystem

internal object DirectStateAccessCompat {
    private val backendField = RenderSystem.getDevice().javaClass.getDeclaredField("backend").apply {
        isAccessible = true
    }

    fun directStateAccess(): DirectStateAccess? = runCatching {
        val backend = backendField.get(RenderSystem.getDevice()) ?: return null
        val method = backend.javaClass.getDeclaredMethod("directStateAccess").apply {
            isAccessible = true
        }
        method.invoke(backend) as? DirectStateAccess
    }.getOrNull()
}
