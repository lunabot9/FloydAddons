package gg.floyd.features.impl.hiders

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import java.util.concurrent.atomic.AtomicLong

object FloydHiders : Module(
    name = "Hiders",
    category = Category.HIDERS,
    description = "Floyd hider toggles for overlays, particles, armor, arrows, tab ping, and hurt camera.",
    toggled = true,
) {
    val noHurtCamera by BooleanSetting("No Hurt Camera", false, desc = "Suppresses hurt camera shake.")
    val removeFireOverlay by BooleanSetting("Remove Fire Overlay", false, desc = "Suppresses the first-person fire overlay.")
    val disableHungerBar by BooleanSetting("Disable Hunger Bar", false, desc = "Hides the hunger bar.")
    val hidePotionEffects by BooleanSetting("Hide Potion Effects", false, desc = "Suppresses the potion effect HUD.")
    val thirdPersonCrosshair by BooleanSetting("3rd Person Crosshair", false, desc = "Allows the crosshair outside first person.")
    val hideEntityFire by BooleanSetting("Hide Entity Fire", false, desc = "Suppresses fire rendering on entities.")
    val disableAttachedArrows by BooleanSetting("Disable Arrows", false, desc = "Hides arrows stuck in player models.")
    val removeFallingBlocks by BooleanSetting("Remove Falling Blocks", false, desc = "Suppresses falling block entity rendering.")
    val removeExplosionParticles by BooleanSetting("No Explosion Particles", false, desc = "Suppresses explosion particles.")
    val removeTabPing by BooleanSetting("Remove Tab Ping", false, desc = "Hides latency icons from the tab list.")
    val serverIdHider by BooleanSetting("Server ID Hider", false, desc = "Replaces Hypixel server IDs in rendered text.")
    val profileIdHider by BooleanSetting("Profile ID Hider", true, desc = "Replaces profile UUID lines in rendered text.")
    val noArmorMode by SelectorSetting("Target", "Off", listOf("Off", "Self", "Others", "All"), desc = "Matches Floyd's armor hiding mode.")

    private val hurtCameraHits = AtomicLong()
    private val fireOverlayHits = AtomicLong()
    private val hungerBarHits = AtomicLong()
    private val potionEffectsHits = AtomicLong()
    private val thirdPersonCrosshairHits = AtomicLong()
    private val entityFireHits = AtomicLong()
    private val attachedArrowHits = AtomicLong()
    private val fallingBlockHits = AtomicLong()
    private val explosionParticleHits = AtomicLong()
    private val tabPingHits = AtomicLong()
    private val armorLayerHits = AtomicLong()
    private val headLayerHits = AtomicLong()

    @JvmStatic fun shouldSuppressHurtCamera(): Boolean = enabled && noHurtCamera
    @JvmStatic fun shouldRemoveFireOverlay(): Boolean = enabled && removeFireOverlay
    @JvmStatic fun shouldDisableHungerBar(): Boolean = enabled && disableHungerBar
    @JvmStatic fun shouldHidePotionEffects(): Boolean = enabled && hidePotionEffects
    @JvmStatic fun shouldShowThirdPersonCrosshair(): Boolean = enabled && thirdPersonCrosshair
    @JvmStatic fun shouldHideEntityFire(): Boolean = enabled && hideEntityFire
    @JvmStatic fun shouldDisableAttachedArrows(): Boolean = enabled && disableAttachedArrows
    @JvmStatic fun shouldRemoveFallingBlocks(): Boolean = enabled && removeFallingBlocks
    @JvmStatic fun shouldRemoveExplosionParticles(): Boolean = enabled && removeExplosionParticles
    @JvmStatic fun shouldRemoveTabPing(): Boolean = enabled && removeTabPing

    @JvmStatic fun recordHurtCamera() { hurtCameraHits.incrementAndGet() }
    @JvmStatic fun recordFireOverlay() { fireOverlayHits.incrementAndGet() }
    @JvmStatic fun recordHungerBar() { hungerBarHits.incrementAndGet() }
    @JvmStatic fun recordPotionEffects() { potionEffectsHits.incrementAndGet() }
    @JvmStatic fun recordThirdPersonCrosshair() { thirdPersonCrosshairHits.incrementAndGet() }
    @JvmStatic fun recordEntityFire() { entityFireHits.incrementAndGet() }
    @JvmStatic fun recordAttachedArrows() { attachedArrowHits.incrementAndGet() }
    @JvmStatic fun recordFallingBlocks() { fallingBlockHits.incrementAndGet() }
    @JvmStatic fun recordExplosionParticles() { explosionParticleHits.incrementAndGet() }
    @JvmStatic fun recordTabPing() { tabPingHits.incrementAndGet() }
    @JvmStatic fun recordArmorLayer() { armorLayerHits.incrementAndGet() }
    @JvmStatic fun recordHeadLayer() { headLayerHits.incrementAndGet() }

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "settings" to mapOf(
            "noHurtCamera" to shouldSuppressHurtCamera(),
            "removeFireOverlay" to shouldRemoveFireOverlay(),
            "disableHungerBar" to shouldDisableHungerBar(),
            "hidePotionEffects" to shouldHidePotionEffects(),
            "thirdPersonCrosshair" to shouldShowThirdPersonCrosshair(),
            "hideEntityFire" to shouldHideEntityFire(),
            "disableAttachedArrows" to shouldDisableAttachedArrows(),
            "removeFallingBlocks" to shouldRemoveFallingBlocks(),
            "removeExplosionParticles" to shouldRemoveExplosionParticles(),
            "removeTabPing" to shouldRemoveTabPing(),
            "serverIdHider" to serverIdHider,
            "profileIdHider" to profileIdHider,
            "noArmorMode" to noArmorModeName()
        ),
        "hookHits" to mapOf(
            "hurtCamera" to hurtCameraHits.get(),
            "fireOverlay" to fireOverlayHits.get(),
            "hungerBar" to hungerBarHits.get(),
            "potionEffects" to potionEffectsHits.get(),
            "thirdPersonCrosshair" to thirdPersonCrosshairHits.get(),
            "entityFire" to entityFireHits.get(),
            "attachedArrows" to attachedArrowHits.get(),
            "fallingBlocks" to fallingBlockHits.get(),
            "explosionParticles" to explosionParticleHits.get(),
            "tabPing" to tabPingHits.get(),
            "armorLayer" to armorLayerHits.get(),
            "headLayer" to headLayerHits.get()
        )
    )

    @JvmStatic
    fun shouldHideArmorFor(entityId: Int): Boolean {
        if (!enabled) return false
        val player = mc.player ?: return false
        val isSelf = entityId == player.id
        return when (noArmorModeName()) {
            "Self" -> isSelf
            "Others" -> !isSelf
            "All" -> true
            else -> false
        }
    }

    private fun noArmorModeName(): String = when (noArmorMode) {
        1 -> "Self"
        2 -> "Others"
        3 -> "All"
        else -> "Off"
    }
}
