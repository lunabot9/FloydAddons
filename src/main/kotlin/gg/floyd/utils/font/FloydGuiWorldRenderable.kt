package gg.floyd.utils.font

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Matrix4fc

/** Keep the MSDF texture, geometry and pipeline together in both GUI and world rendering.
 * Third-party renderers that choose their own shader must use Floyd's MSDF compatibility hook. */
class FloydGuiWorldRenderable(
    @Suppress("UNUSED_PARAMETER") worldText: TextRenderable.Styled,
    private val guiText: TextRenderable.Styled,
) : TextRenderable.Styled by guiText {
    override fun render(pose: Matrix4fc, buffer: VertexConsumer, light: Int, gui: Boolean) {
        guiText.render(pose, buffer, light, gui)
    }

    override fun renderType(displayMode: Font.DisplayMode): RenderType = guiText.renderType(displayMode)
}
