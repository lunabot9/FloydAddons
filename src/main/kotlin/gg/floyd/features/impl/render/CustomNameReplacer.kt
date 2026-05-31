package gg.floyd.features.impl.render

import gg.floyd.features.impl.player.FloydNickHider
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

object CustomNameReplacer {
    @JvmStatic
    fun isEnabled() = FloydNickHider.hasReplacements()

    @JvmStatic
    fun replaceStringIfNeeded(text: String): String = FloydNickHider.replaceString(text)

    @JvmStatic
    fun replaceComponentIfNeeded(component: Component): Component? = FloydNickHider.replaceComponent(component)

    @JvmStatic
    fun replaceSequenceIfNeeded(text: FormattedCharSequence): FormattedCharSequence = FloydNickHider.replaceSequence(text)
}
