package floydaddons.not.dogshit.client.features.impl.hiders

import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.clickgui.settings.impl.SelectorSetting
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module
import java.util.concurrent.atomic.AtomicLong

object FloydNoHurtCamera : Module(name = "No Hurt Camera", category = Category.HIDERS, description = "Suppresses hurt camera shake.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldSuppressHurtCamera(): Boolean = enabled
    @JvmStatic fun recordHurtCamera() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydRemoveFireOverlay : Module(name = "Remove Fire Overlay", category = Category.HIDERS, description = "Suppresses the first-person fire overlay.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldRemoveFireOverlay(): Boolean = enabled
    @JvmStatic fun recordFireOverlay() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydDisableHungerBar : Module(name = "Disable Hunger Bar", category = Category.HIDERS, description = "Hides the hunger bar.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldDisableHungerBar(): Boolean = enabled
    @JvmStatic fun recordHungerBar() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydHidePotionEffects : Module(name = "Hide Potion Effects", category = Category.HIDERS, description = "Suppresses the potion effect HUD.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldHidePotionEffects(): Boolean = enabled
    @JvmStatic fun recordPotionEffects() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydThirdPersonCrosshair : Module(name = "3rd Person Crosshair", category = Category.HIDERS, description = "Allows the crosshair outside first person.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldShowThirdPersonCrosshair(): Boolean = enabled
    @JvmStatic fun recordThirdPersonCrosshair() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydHideEntityFire : Module(name = "Hide Entity Fire", category = Category.HIDERS, description = "Suppresses fire rendering on entities.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldHideEntityFire(): Boolean = enabled
    @JvmStatic fun recordEntityFire() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydDisableArrows : Module(name = "Disable Arrows", category = Category.HIDERS, description = "Hides arrows stuck in player models.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldDisableAttachedArrows(): Boolean = enabled
    @JvmStatic fun recordAttachedArrows() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydRemoveFallingBlocks : Module(name = "Remove Falling Blocks", category = Category.HIDERS, description = "Suppresses falling block entity rendering.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldRemoveFallingBlocks(): Boolean = enabled
    @JvmStatic fun recordFallingBlocks() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydRemoveExplosionParticles : Module(name = "No Explosion Particles", category = Category.HIDERS, description = "Suppresses explosion particles.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldRemoveExplosionParticles(): Boolean = enabled
    @JvmStatic fun recordExplosionParticles() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydRemoveTabPing : Module(name = "Remove Tab Ping", category = Category.HIDERS, description = "Hides latency icons from the tab list.") {
    private val hits = AtomicLong()
    @JvmStatic fun shouldRemoveTabPing(): Boolean = enabled
    @JvmStatic fun recordTabPing() { hits.incrementAndGet() }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "hits" to hits.get())
}

object FloydHideWatchdogMessages : Module(name = "Hide Watchdog Message", category = Category.HIDERS, description = "Suppresses Hypixel Watchdog announcement spam.") {
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled)
}

object FloydModHider : Module(name = "Mod Hider", category = Category.HIDERS, description = "Hides floydaddons from basic Fabric loader lookups.") {
    @JvmStatic fun shouldHideLoaderEntry(): Boolean = enabled
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled)
}

object FloydServerIdHider : Module(name = "Server ID Hider", category = Category.HIDERS, description = "Replaces Hypixel server IDs in rendered text.") {
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled)
}

object FloydProfileIdHider : Module(name = "Profile ID Hider", category = Category.HIDERS, description = "Replaces profile UUID lines in rendered text.", toggled = true) {
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled)
}

object FloydNoArmor : Module(name = "No Armor", category = Category.HIDERS, description = "Matches Floyd's armor hiding mode.") {
    var noArmorMode by SelectorSetting("Target", "Off", listOf("Off", "Self", "Others", "All"), desc = "Which entities should have armor hidden.")
    private val armorLayerHits = AtomicLong()
    private val headLayerHits = AtomicLong()

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

    fun cycleTarget() {
        noArmorMode = (noArmorMode + 1) % 4
        if (!enabled) toggle()
    }

    @JvmStatic fun recordArmorLayer() { armorLayerHits.incrementAndGet() }
    @JvmStatic fun recordHeadLayer() { headLayerHits.incrementAndGet() }

    fun targetName(): String = noArmorModeName()

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "target" to noArmorModeName(),
        "hits" to mapOf(
            "armorLayer" to armorLayerHits.get(),
            "headLayer" to headLayerHits.get()
        )
    )

    private fun noArmorModeName(): String = when (noArmorMode) {
        1 -> "Self"
        2 -> "Others"
        3 -> "All"
        else -> "Off"
    }
}
