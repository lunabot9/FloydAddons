package gg.floyd.utils.font

import com.mojang.blaze3d.vertex.VertexConsumer
import java.lang.reflect.Proxy
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertSame
import com.mojang.blaze3d.textures.FilterMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FloydGuiWorldRenderableTest {
    @Test fun `GUI and every world display mode keep MSDF geometry paired with its shader`() {
        val calls = mutableListOf<String>()
        fun text(surface: String): TextRenderable.Styled = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(TextRenderable.Styled::class.java),
        ) { _, method, args ->
            when (method.name) {
                "render" -> { calls += "$surface:${args!![3]}"; null }
                "renderType", "textureView", "guiPipeline" -> error("$surface:${method.name}")
                else -> 0f
            }
        } as TextRenderable.Styled
        val buffer = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(VertexConsumer::class.java)) {
            _, _, _ -> error("Unexpected vertex emission")
        } as VertexConsumer
        val text = FloydGuiWorldRenderable(text("standard"), text("msdf"))
        text.render(Matrix4f(), buffer, 0, true)
        text.render(Matrix4f(), buffer, 0, false)
        assertEquals(listOf("msdf:true", "msdf:false"), calls)
        for (mode in Font.DisplayMode.entries) {
            assertEquals("msdf:renderType", assertFailsWith<IllegalStateException> { text.renderType(mode) }.message)
        }
        assertEquals("msdf:textureView", assertFailsWith<IllegalStateException> { text.textureView() }.message)
        assertEquals("msdf:guiPipeline", assertFailsWith<IllegalStateException> { text.guiPipeline() }.message)
        // Skyblocker bypasses renderType and chooses a shader from guiPipeline instead.
        val original = MsdfPipelines.GUI
        assertSame(MsdfPipelines.TEXT, SkyblockerMsdfCompat.pipeline(original, false, text))
        assertSame(MsdfPipelines.TEXT_SEE_THROUGH, SkyblockerMsdfCompat.pipeline(original, true, text))
        assertEquals(FilterMode.LINEAR, SkyblockerMsdfCompat.filter(FilterMode.NEAREST, text))
        val vanilla = text("standard")
        assertSame(original, SkyblockerMsdfCompat.pipeline(original, true, vanilla))
        assertEquals(FilterMode.NEAREST, SkyblockerMsdfCompat.filter(FilterMode.NEAREST, vanilla))
    }
}
