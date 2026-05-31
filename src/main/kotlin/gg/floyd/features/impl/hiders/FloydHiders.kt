package gg.floyd.features.impl.hiders

import java.util.concurrent.atomic.AtomicLong

/**
 * Facade over the per-feature hider modules.
 *
 * This used to be a single mega-[Module][gg.floyd.features.Module] that owned every hider toggle.
 * Each hider is now its own top-level module (see [FloydHiderModules.kt]); this object keeps the
 * shared hit counters used by the mixins and exposes facade methods that read the new modules so the
 * mixins do not have to change.
 */
object FloydHiders {

    val serverIdHider get() = FloydServerIdHider.enabled
    val profileIdHider get() = FloydProfileIdHider.enabled

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

    @JvmStatic fun shouldSuppressHurtCamera(): Boolean = FloydNoHurtCamera.enabled
    @JvmStatic fun shouldRemoveFireOverlay(): Boolean = FloydRemoveFireOverlay.enabled
    @JvmStatic fun shouldDisableHungerBar(): Boolean = FloydDisableHungerBar.enabled
    @JvmStatic fun shouldHidePotionEffects(): Boolean = FloydHidePotionEffects.enabled
    @JvmStatic fun shouldShowThirdPersonCrosshair(): Boolean = FloydThirdPersonCrosshair.enabled
    @JvmStatic fun shouldHideEntityFire(): Boolean = FloydHideEntityFire.enabled
    @JvmStatic fun shouldDisableAttachedArrows(): Boolean = FloydDisableArrows.enabled
    @JvmStatic fun shouldRemoveFallingBlocks(): Boolean = FloydRemoveFallingBlocks.enabled
    @JvmStatic fun shouldRemoveExplosionParticles(): Boolean = FloydRemoveExplosionParticles.enabled
    @JvmStatic fun shouldRemoveTabPing(): Boolean = FloydRemoveTabPing.enabled

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
    fun shouldHideArmorFor(entityId: Int): Boolean = FloydNoArmor.shouldHideArmorFor(entityId)

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
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
            "noArmorMode" to FloydNoArmor.modeName()
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
}
