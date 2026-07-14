package gg.floyd.utils.render

internal fun guiToNvgScale(guiScale: Float, devicePixelRatio: Float): Float =
    guiScale / devicePixelRatio.coerceAtLeast(0.0001f)

internal fun pipContentOffset(screenOrigin: Float, guiScale: Float, devicePixelRatio: Float): Float =
    -screenOrigin * guiToNvgScale(guiScale, devicePixelRatio)

/** Converts a coordinate drawn by NanoVG into the GUI-space coordinate expected by a PIP state. */
internal fun nvgToGuiCoordinate(nvgCoordinate: Float, renderScale: Float, guiScale: Float): Float =
    nvgCoordinate * renderScale / guiScale.coerceAtLeast(0.0001f)
