// SOURCE: https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/noammaddons/features/impl/misc/Cosmetics.kt
// Cosmetic rendering: Dragon Wings, Angel Halo, Custom Player Size
// CosmeticRendering is added as a LayerRenderer to the player skin map

// KEY PATTERN for FloydAddons: This is equivalent to FloydAddons' FeatureRenderer system.
// FloydAddons already has CapeFeatureRenderer and ConeFeatureRenderer.
// Dragon wings would follow the same pattern.

package noammaddons.features.impl.misc

import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.model.ModelBase
import net.minecraft.client.model.ModelRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.client.renderer.entity.layers.LayerRenderer
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.RenderPlayerEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import noammaddons.NoammAddons.Companion.MOD_ID
import noammaddons.NoammAddons.Companion.mc
import noammaddons.features.impl.misc.PlayerModel.getPlayerScaleFactor
import noammaddons.utils.DataDownloader
import noammaddons.utils.RenderHelper.partialTicks
import kotlin.math.cos
import kotlin.math.sin

object Cosmetics {
    private var devPlayers = DataDownloader.loadJson<List<String>>("devPlayers.json")

    // Dragon wing model using Ender Dragon texture
    object DragonWings: ModelBase() {
        private val dragonWingTextureLocation = ResourceLocation("textures/entity/enderdragon/dragon.png")
        private val wing: ModelRenderer
        private val wingTip: ModelRenderer

        init {
            textureWidth = 256; textureHeight = 256
            setTextureOffset("wing.skin", -56, 88)
            setTextureOffset("wingtip.skin", -56, 144)
            setTextureOffset("wing.bone", 112, 88)
            setTextureOffset("wingtip.bone", 112, 136)
            wing = ModelRenderer(this, "wing")
            wing.setRotationPoint(-12f, 5f, 2f)
            wing.addBox("bone", -56.0f, -4f, -4f, 56, 8, 8)
            wing.addBox("skin", -56.0f, 0f, 2f, 56, 0, 56)
            wingTip = ModelRenderer(this, "wingtip")
            wingTip.setRotationPoint(-56.0f, 0f, 0f)
            wingTip.addBox("bone", -56.0f, -2f, -2f, 56, 4, 4)
            wingTip.addBox("skin", -56.0f, 0f, 2f, 56, 0, 56)
            wing.addChild(wingTip)
        }

        fun renderWings(player: EntityPlayer, devID: String?) {
            if (player.uniqueID.toString() != devID) return
            val scale = getPlayerScaleFactor(player)
            GlStateManager.pushMatrix()
            GlStateManager.scale(0.2 * scale, 0.2 * scale, 0.2 * scale)
            GlStateManager.translate(0.0, 0.45, 0.1 / 0.2 / scale)
            if (player.isSneaking) GlStateManager.translate(0.0, 0.125 * scale, 0.0)
            GlStateManager.color(1f, 1f, 1f, 1f)
            mc.textureManager.bindTexture(dragonWingTextureLocation)
            for (j in 0..1) {
                GlStateManager.enableCull()
                val f11 = System.currentTimeMillis() % 1000 / 1000f * Math.PI.toFloat() * 2f
                wing.rotateAngleX = Math.toRadians(-80.0).toFloat() - cos(f11) * 0.2f
                wing.rotateAngleY = Math.toRadians(20.0).toFloat() + sin(f11) * 0.4f
                wing.rotateAngleZ = Math.toRadians(20.0).toFloat()
                wingTip.rotateAngleZ = -(sin((f11 + 2f)) + 0.5).toFloat() * 0.75f
                wing.render(0.0625f)
                GlStateManager.scale(-1f, 1f, 1f)
                if (j == 0) GlStateManager.cullFace(1028)
            }
            GlStateManager.cullFace(1029)
            GlStateManager.disableCull()
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.popMatrix()
        }
    }

    // Angel halo using voxel-based ring of 1x1x1 cubes
    object AngelHalo: ModelBase() {
        private val haloTexture = ResourceLocation(MOD_ID, "textures/HaloTexture.png")
        private val halo: ModelRenderer
        // ... (40+ addBox calls forming a circle) ...

        fun drawHalo(player: EntityPlayer, devID: String?) {
            if (player.uniqueID.toString() != devID) return
            // Rotate halo independently of player look direction
            val rotation = interpolate(player.prevRenderYawOffset, player.renderYawOffset)
            val scale = getPlayerScaleFactor(player)
            GlStateManager.pushMatrix()
            GlStateManager.translate(0f, -0.3f * scale, 0f)
            GlStateManager.scale(scale, scale, scale)
            val rotationAngle = (System.currentTimeMillis() % 3600) / 10f
            GlStateManager.rotate(rotationAngle - rotation, 0f, 1f, 0f)
            if (player.isSneaking) GlStateManager.translate(0f, 0.3f * scale, 0f)
            GlStateManager.color(1f, 1f, 0f, 1f) // Yellow
            mc.textureManager.bindTexture(haloTexture)
            halo.render(0.0625f)
            GlStateManager.popMatrix()
        }
    }
}
