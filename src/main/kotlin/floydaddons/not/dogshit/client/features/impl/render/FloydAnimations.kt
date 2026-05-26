package floydaddons.not.dogshit.client.features.impl.render

import floydaddons.not.dogshit.client.clickgui.settings.impl.BooleanSetting
import floydaddons.not.dogshit.client.clickgui.settings.impl.NumberSetting
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module
import java.util.concurrent.atomic.AtomicLong

object FloydAnimations : Module(
    name = "Animations",
    category = Category.RENDER,
    description = "Floyd held-item animation controls.",
    toggled = false,
) {
    val posX by NumberSetting("Pos X", 0, -150, 150, 1, desc = "First-person item X offset.")
    val posY by NumberSetting("Pos Y", 0, -150, 150, 1, desc = "First-person item Y offset.")
    val posZ by NumberSetting("Pos Z", 0, -150, 50, 1, desc = "First-person item Z offset.")
    val rotX by NumberSetting("Rot X", 0, -180, 180, 1, desc = "First-person item X rotation.")
    val rotY by NumberSetting("Rot Y", 0, -180, 180, 1, desc = "First-person item Y rotation.")
    val rotZ by NumberSetting("Rot Z", 0, -180, 180, 1, desc = "First-person item Z rotation.")
    val scale by NumberSetting("Scale", 1.0f, 0.1f, 2.0f, 0.05f, desc = "First-person item scale.")
    val swingDuration by NumberSetting("Swing Duration", 6, 1, 100, 1, desc = "Ticks for a complete swing animation.")
    val cancelReEquip by BooleanSetting("Cancel Re-Equip", false, desc = "Removes the held-item re-equip bob.")
    val hidePlayerHand by BooleanSetting("Hide Hand", false, desc = "Hides the main hand when no item is held.")
    val classicClick by BooleanSetting("Classic Click", false, desc = "Forces toggle key mappings into hold mode.")

    private val customSwingDurationHits = AtomicLong()
    private val preventedSwingRestartHits = AtomicLong()
    private val itemTransformHits = AtomicLong()
    private val hideEmptyMainHandHits = AtomicLong()
    private val cancelReEquipHits = AtomicLong()
    private val classicClickHits = AtomicLong()

    @JvmStatic fun shouldApply(): Boolean = enabled
    @JvmStatic fun xOffset(): Float = posX / 100.0f
    @JvmStatic fun yOffset(): Float = posY / 100.0f
    @JvmStatic fun zOffset(): Float = posZ / 100.0f
    @JvmStatic fun xRotation(): Float = rotX.toFloat()
    @JvmStatic fun yRotation(): Float = rotY.toFloat()
    @JvmStatic fun zRotation(): Float = rotZ.toFloat()
    @JvmStatic fun itemScale(): Float = scale
    @JvmStatic fun swingTicks(): Int = swingDuration
    @JvmStatic fun shouldCancelReEquip(): Boolean = enabled && cancelReEquip
    @JvmStatic fun shouldHideEmptyMainHand(): Boolean = enabled && hidePlayerHand
    @JvmStatic fun shouldUseClassicClick(): Boolean = enabled && classicClick

    @JvmStatic fun recordCustomSwingDuration() { customSwingDurationHits.incrementAndGet() }
    @JvmStatic fun recordPreventedSwingRestart() { preventedSwingRestartHits.incrementAndGet() }
    @JvmStatic fun recordItemTransform() { itemTransformHits.incrementAndGet() }
    @JvmStatic fun recordHideEmptyMainHand() { hideEmptyMainHandHits.incrementAndGet() }
    @JvmStatic fun recordCancelReEquip() { cancelReEquipHits.incrementAndGet() }
    @JvmStatic fun recordClassicClick() { classicClickHits.incrementAndGet() }

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "position" to mapOf("x" to xOffset(), "y" to yOffset(), "z" to zOffset()),
        "rotation" to mapOf("x" to xRotation(), "y" to yRotation(), "z" to zRotation()),
        "scale" to itemScale(),
        "swingDuration" to swingTicks(),
        "cancelReEquip" to shouldCancelReEquip(),
        "hideEmptyMainHand" to shouldHideEmptyMainHand(),
        "classicClick" to shouldUseClassicClick(),
        "settings" to mapOf(
            "posX" to posX,
            "posY" to posY,
            "posZ" to posZ,
            "rotX" to rotX,
            "rotY" to rotY,
            "rotZ" to rotZ,
            "scale" to scale,
            "swingDuration" to swingDuration,
            "cancelReEquip" to cancelReEquip,
            "hideHand" to hidePlayerHand,
            "classicClick" to classicClick
        ),
        "hookHits" to mapOf(
            "customSwingDuration" to customSwingDurationHits.get(),
            "preventedSwingRestart" to preventedSwingRestartHits.get(),
            "itemTransform" to itemTransformHits.get(),
            "hideEmptyMainHand" to hideEmptyMainHandHits.get(),
            "cancelReEquip" to cancelReEquipHits.get(),
            "classicClick" to classicClickHits.get()
        )
    )
}
