// SOURCE: https://github.com/odtheking/Odin/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/skyblock/AutoClicker.kt
package me.odinclient.features.impl.skyblock

import me.odinclient.utils.skyblock.PlayerUtils.leftClick
import me.odinclient.utils.skyblock.PlayerUtils.rightClick
import me.odinmain.clickgui.settings.Setting.Companion.withDependency
import me.odinmain.clickgui.settings.impl.BooleanSetting
import me.odinmain.clickgui.settings.impl.KeybindSetting
import me.odinmain.clickgui.settings.impl.NumberSetting
import me.odinmain.features.Module
import me.odinmain.utils.skyblock.isHolding
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.input.Keyboard

object AutoClicker : Module(
    name = "Auto Clicker",
    description = "Auto clicker with options for left-click, right-click, or both."
) {
    private val terminatorOnly by BooleanSetting("Terminator Only", true)
    private val cps by NumberSetting("Clicks Per Second", 5.0f, 3.0, 15.0, .5).withDependency { terminatorOnly }
    private val enableLeftClick by BooleanSetting("Enable Left Click", true).withDependency { !terminatorOnly }
    private val enableRightClick by BooleanSetting("Enable Right Click", true).withDependency { !terminatorOnly }
    private val leftCps by NumberSetting("Left Clicks Per Second", 5.0f, 3.0, 15.0, .5).withDependency { !terminatorOnly }
    private val rightCps by NumberSetting("Right Clicks Per Second", 5.0f, 3.0, 15.0, .5).withDependency { !terminatorOnly }
    private val leftClickKeybind by KeybindSetting("Left Click", Keyboard.KEY_NONE).withDependency { !terminatorOnly }
    private val rightClickKeybind by KeybindSetting("Right Click", Keyboard.KEY_NONE).withDependency { !terminatorOnly }

    private var nextLeftClick = .0
    private var nextRightClick = .0

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        if (mc.currentScreen != null) return
        val nowMillis = System.currentTimeMillis()
        if (terminatorOnly) {
            if (!isHolding("TERMINATOR") || !mc.gameSettings.keyBindUseItem.isKeyDown) return
            if (nowMillis < nextRightClick) return
            nextRightClick = nowMillis + ((1000 / cps) + ((Math.random() - .5) * 60.0))
            leftClick()
        } else {
            if (enableLeftClick && leftClickKeybind.isDown() && nowMillis >= nextLeftClick) {
                nextLeftClick = nowMillis + ((1000 / leftCps) + ((Math.random() - .5) * 60.0))
                leftClick()
            }
            if (enableRightClick && rightClickKeybind.isDown() && nowMillis >= nextRightClick) {
                nextRightClick = nowMillis + ((1000 / rightCps) + ((Math.random() - .5) * 60.0))
                rightClick()
            }
        }
    }
}
