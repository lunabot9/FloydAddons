package gg.floyd.utils.ui.rendering

internal fun shouldUseImmediateTextPolicy(
    legacyNvgText: Boolean,
    immediateTextOverrideDepth: Int,
    screenClassName: String?,
): Boolean =
    legacyNvgText ||
        immediateTextOverrideDepth > 0 ||
        screenClassName?.startsWith("gg.floyd.clickgui.") == true
