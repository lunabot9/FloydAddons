package gg.floyd.utils.render

internal fun guiToNvgScale(guiScale: Float, devicePixelRatio: Float): Float =
    guiScale / devicePixelRatio.coerceAtLeast(0.0001f)

internal fun pipContentOffset(screenOrigin: Float, guiScale: Float, devicePixelRatio: Float): Float =
    -screenOrigin * guiToNvgScale(guiScale, devicePixelRatio)
