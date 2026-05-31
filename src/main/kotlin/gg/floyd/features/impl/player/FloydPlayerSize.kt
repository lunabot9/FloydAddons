package gg.floyd.features.impl.player

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.moduleToggle
import net.minecraft.world.entity.player.Player

internal object FloydPlayerSizeControls {
    fun isActive(scaleX: Float, scaleY: Float, scaleZ: Float): Boolean =
        scaleX != 1.0f || scaleY != 1.0f || scaleZ != 1.0f

    fun toggledScale(scaleX: Float, scaleY: Float, scaleZ: Float): Float =
        if (isActive(scaleX, scaleY, scaleZ)) 1.0f else 2.0f
}

object FloydPlayerSize : Module(
    name = "Player Size",
    category = Category.PLAYER,
    description = "Floyd player model scale settings.",
    toggled = true,
) {
    private val togglePlayerSizeAction by ActionSetting("Toggle Player Size", desc = "Matches Floyd's Player Size module toggle.") {
        togglePlayerSize()
        ModuleManager.saveConfigurations()
        moduleToggle(name, playerSizeActive())
    }
    var scaleX by NumberSetting("X", 1.0f, -1.0f, 5.0f, 0.05f, desc = "Player X scale.")
    var scaleY by NumberSetting("Y", 1.0f, -1.0f, 5.0f, 0.05f, desc = "Player Y scale.")
    var scaleZ by NumberSetting("Z", 1.0f, -1.0f, 5.0f, 0.05f, desc = "Player Z scale.")
    private val target by SelectorSetting("Target", "Self", listOf("Self", "Real Players", "All"), desc = "Which players receive custom scale.")

    @JvmStatic
    fun shouldScale(id: Int): Boolean {
        if (!enabled || !playerSizeActive()) return false
        val player = mc.player ?: return false
        return when (targetName()) {
            "Self" -> id == player.id
            "Real Players" -> {
                val entity = mc.level?.getEntity(id) as? Player ?: return false
                mc.connection?.getPlayerInfo(entity.uuid) != null
            }
            else -> true
        }
    }

    @JvmStatic
    fun scaleX(): Float = scaleX

    @JvmStatic
    fun scaleY(): Float = scaleY

    @JvmStatic
    fun scaleZ(): Float = scaleZ

    @JvmStatic
    fun negativeScaleYOffset(): Float = kotlin.math.abs(scaleY) * 1.5f

    @JvmStatic
    fun playerSizeActive(): Boolean =
        FloydPlayerSizeControls.isActive(scaleX, scaleY, scaleZ)

    @JvmStatic
    fun togglePlayerSize() {
        val value = FloydPlayerSizeControls.toggledScale(scaleX, scaleY, scaleZ)
        scaleX = value
        scaleY = value
        scaleZ = value
    }

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "active" to playerSizeActive(),
        "settings" to mapOf(
            "scaleX" to scaleX,
            "scaleY" to scaleY,
            "scaleZ" to scaleZ,
            "sizeTarget" to targetName()
        )
    )

    private fun targetName(): String = when (target) {
        0 -> "Self"
        1 -> "Real Players"
        else -> "All"
    }
}
