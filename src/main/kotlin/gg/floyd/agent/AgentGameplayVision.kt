package gg.floyd.agent

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.client.Screenshot
import java.io.File
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** One game framebuffer per requested analysis or enabled gameplay comment. No desktop capture or background recording. */
internal object AgentGameplayVision {
    fun capture(): CompletableFuture<File> {
        val result = CompletableFuture<File>()
        mc.execute {
            try {
                check(mc.level != null) { "Join a world before asking me to look at your gameplay." }
                val name = "floyd-agent-${UUID.randomUUID()}.png"
                val file = File(mc.gameDirectory, "screenshots/$name")
                Screenshot.grab(mc.gameDirectory, name, mc.mainRenderTarget, 1) {
                    try {
                        check(file.isFile) { "The gameplay screenshot could not be captured." }
                        val image = checkNotNull(ImageIO.read(file))
                        if (image.width > 1280 || image.height > 1280) {
                            val scale = 1280.0 / maxOf(image.width, image.height)
                            val scaled = BufferedImage((image.width * scale).roundToInt().coerceAtLeast(1),
                                (image.height * scale).roundToInt().coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
                            val graphics = scaled.createGraphics()
                            try {
                                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                                graphics.drawImage(image, 0, 0, scaled.width, scaled.height, null)
                            } finally { graphics.dispose() }
                            ImageIO.write(scaled, "png", file)
                        }
                        if (!result.complete(file)) file.delete()
                    } catch (e: Exception) { file.delete(); result.completeExceptionally(e) }
                }
            } catch (e: Exception) { result.completeExceptionally(e) }
        }
        return result
    }
}
