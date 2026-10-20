package gg.floyd.clickgui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import gg.floyd.FloydAddonsMod.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.render.pip.GuiEntityRenderer
import net.minecraft.client.renderer.state.gui.GuiRenderState
import gg.floyd.mixin.accessors.GameRendererFogAccessor
import net.minecraft.client.renderer.fog.FogRenderer
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.min

@Composable internal fun FloydModelIcon(id: String, mob: Boolean) = NativePreview(if (mob) "mob" else "block", id, Modifier.size(28.dp))
@Composable internal fun FloydConeModelPreview() = NativePreview("cone", "cone", Modifier.size(250.dp, 210.dp))
@Composable private fun NativePreview(kind: String, id: String, modifier: Modifier) {
    val key = remember(kind, id) { Any() }
    DisposableEffect(key) { onDispose { FloydSettingsModelPreviews.remove(key) } }
    Box(modifier.onGloballyPositioned { FloydSettingsModelPreviews.place(key, kind, id, it.boundsInWindow()) })
}

/** Submit native items/entities after Compose, at its clipped on-screen coordinates. */
object FloydSettingsModelPreviews {
    private data class Preview(val kind: String, val id: String, val bounds: Rect)
    private val previews = linkedMapOf<Any, Preview>()
    private val mobs = hashMapOf<String, LivingEntity?>()
    private var cachedLevel: Any? = null
    private var conePlayer: RemotePlayer? = null
    private var lastRendered = emptyList<String>()
    private val guiState = GuiRenderState()
    private var guiRenderer: GuiRenderer? = null
    internal fun place(key: Any, kind: String, id: String, bounds: Rect) { previews[key] = Preview(kind, id, bounds) }
    internal fun remove(key: Any) { previews.remove(key) }
    @JvmStatic fun isConePreviewEntity(id: Int): Boolean = conePlayer?.id == id && mc.screen is FloydSettingsScreen
    fun state(): Map<String, Any?> = mapOf("rendered" to lastRendered, "conePreview" to lastRendered.contains("cone:cone"),
        "bounds" to previews.values.map { mapOf("kind" to it.kind, "id" to it.id, "x" to it.bounds.left, "y" to it.bounds.top, "w" to it.bounds.width, "h" to it.bounds.height) })
    fun close() {
        guiRenderer?.close(); guiRenderer = null; lastRendered = emptyList()
        mobs.clear(); conePlayer = null; cachedLevel = null
    }
    @JvmStatic fun renderAfterCompose() {
        if (mc.screen !is FloydSettingsScreen) {
            close()
            return
        }
        val renderer = guiRenderer ?: createRenderer().also { guiRenderer = it }
        val graphics = GuiGraphicsExtractor(mc, guiState, 0, 0)
        // The vanilla entity PIP owns one texture. Flush each entity before another can overwrite it.
        try { render(graphics) { flush(renderer) }; flush(renderer) }
        finally { renderer.endFrame() }
    }
    private fun createRenderer(): GuiRenderer {
        //? if >=26.2 {
        /*return GuiRenderer(guiState, mc.gameRenderer.featureRenderDispatcher,
            listOf(GuiEntityRenderer(mc.entityRenderDispatcher)))
        *///?} else {
        return GuiRenderer(guiState, mc.renderBuffers().bufferSource(), mc.gameRenderer.submitNodeStorage,
            mc.gameRenderer.featureRenderDispatcher, listOf(GuiEntityRenderer(mc.renderBuffers().bufferSource(), mc.entityRenderDispatcher)))
        //?}
    }
    private fun flush(renderer: GuiRenderer) {
        //? if >=26.2 {
        /*renderer.render()
        *///?} else {
        renderer.render((mc.gameRenderer as GameRendererFogAccessor).`floydaddons$getFogRenderer`().getBuffer(FogRenderer.FogMode.NONE))
        //?}
    }
    private fun render(graphics: GuiGraphicsExtractor, flushEntities: () -> Unit) {
        if (cachedLevel !== mc.level) { cachedLevel = mc.level; mobs.clear(); conePlayer = null }
        val scale = mc.window.width.toFloat() / mc.window.guiScaledWidth
        val rendered = mutableListOf<String>()
        graphics.nextStratum()
        for (preview in previews.values.toList()) {
            val b = preview.bounds
            if (b.width < 4 || b.height < 4) continue
            val l = (b.left / scale).toInt(); val t = (b.top / scale).toInt()
            val r = (b.right / scale).toInt(); val bottom = (b.bottom / scale).toInt()
            if (r <= l || bottom <= t) continue
            graphics.enableScissor(l, t, r, bottom)
            try {
                when (preview.kind) {
                    "block" -> {
                        val id = Identifier.tryParse(preview.id) ?: continue
                        val block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null) ?: continue
                        val item = block.asItem().takeUnless { it === Items.AIR } ?: when (id.path) {
                            "water" -> Items.WATER_BUCKET; "lava" -> Items.LAVA_BUCKET; else -> Items.BARRIER
                        }
                        val s = min(r-l, bottom-t) / 16f
                        graphics.pose().pushMatrix(); graphics.pose().translate(l.toFloat(), t.toFloat()); graphics.pose().scale(s, s)
                        graphics.item(ItemStack(item), 0, 0); graphics.pose().popMatrix()
                    }
                    "mob", "cone" -> {
                        val level = mc.level ?: continue
                        val entity = if (preview.kind == "cone") {
                            val player = mc.player ?: continue
                            val clone = conePlayer ?: RemotePlayer(level, player.gameProfile).also { conePlayer = it }
                            for (slot in EquipmentSlot.entries) clone.setItemSlot(slot, player.getItemBySlot(slot).copy())
                            clone
                        } else mobs.getOrPut(preview.id) {
                            val id = Identifier.tryParse(preview.id)
                            if (id != null && preview.id.contains(':')) BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null)?.create(level, EntitySpawnReason.COMMAND) as? LivingEntity
                            else level.entitiesForRendering().filterIsInstance<LivingEntity>().firstOrNull { it.name.string.equals(preview.id, true) }
                        } ?: continue
                        val size = if (preview.kind == "cone") ((bottom-t) / 2.9f).toInt()
                            else (min(r-l, bottom-t) / (maxOf(entity.bbHeight, entity.bbWidth) * 1.35f)).toInt().coerceAtLeast(1)
                        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, l, t, r, bottom, size, .0625f, (l+r)/2f, (t+bottom)/2f, entity)
                        guiState.forEachPictureInPicture {
                            if (it is net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState) {
                                it.renderState().nameTag = null
                                it.renderState().scoreText = null
                            }
                        }
                    }
                }
                rendered += "${preview.kind}:${preview.id}"
            } finally { graphics.disableScissor() }
            if (preview.kind != "block") flushEntities()
        }
        lastRendered = rendered
    }
}
