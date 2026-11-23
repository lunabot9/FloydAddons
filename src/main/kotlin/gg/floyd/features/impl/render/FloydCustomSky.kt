package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.utils.render.SkyGpuTimer
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.misc.FloydMenuVideoBackground
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4f
import org.joml.Matrix4fc

/** Direction-based sky rendering: camera rotation/FOV change the view, never the sky's position. */
object FloydCustomSky : Module(
    name = "Custom Sky",
    category = Category.RENDER,
    description = "Eight animated 360-degree skies, from auroras and nebulae to sunny clouds.",
) {
    private val styles = listOf("Golden Aurora", "Nebula", "Emerald Aurora", "Crimson Eclipse", "Sapphire Galaxy", "Rose Clouds", "Midnight Aurora", "Cloud Nine")
    private val skyStyle by SelectorSetting("Sky Style", "Golden Aurora", styles, desc = "Choose the animated sky surrounding the world. Midnight Aurora matches the current main menu palette.")
    private val animationSpeed by NumberSetting("Animation Speed", 1f, .1f, 3f, .1f, desc = "Speed of the sky animation. Golden Aurora and Midnight Aurora also follow your menu background settings.")
    private val qualities = listOf("Performance", "Balanced", "High")
    private val quality by SelectorSetting("Render Quality", "Balanced", qualities, desc = "Cloud Nine uses cached procedural noise and dense cloud sampling: Balanced renders at 85% dimensions, Performance at 65%, and High at full resolution. Other volumetric skies use half/third resolution; stars remain sharp and lighter skies stay native.")
    private val inverseProjectionView = Matrix4f()
    private var cameraReady = false
    private var renderedThisFrame = false
    private var renderedFrames = 0L
    private var lastStyle = ""

    @JvmStatic
    fun beginFrame(camera: CameraRenderState, viewRotation: Matrix4fc) {
        renderedThisFrame = false
        cameraReady = enabled && mc.level != null
        if (cameraReady) inverseProjectionView.set(camera.projectionMatrix).mul(viewRotation).invert()
    }

    @JvmStatic
    fun renderSky(): Boolean {
        if (!enabled || !cameraReady || mc.level == null) return false
        SkyGpuTimer.begin()
        try {
            renderedThisFrame = FloydMenuVideoBackground.renderWorldSky(inverseProjectionView, skyStyle + 1, animationSpeed, quality)
        } finally { SkyGpuTimer.end() }
        if (renderedThisFrame) { renderedFrames++; lastStyle = styles[skyStyle] }
        return renderedThisFrame
    }

    @JvmStatic
    fun replacesVanillaSky(): Boolean = enabled && renderedThisFrame

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "style" to styles[skyStyle],
        "styles" to styles,
        "quality" to qualities[quality],
        "resolutionScale" to FloydMenuVideoBackground.worldSkyResolutionScale(skyStyle + 1, quality),
        "cloudSamples" to if (skyStyle == 7) 112 else null,
        "history" to if (skyStyle == 7) FloydMenuVideoBackground.cloudHistoryState() else null,
        "gpuProfile" to SkyGpuTimer.state(),
        "renderer" to "procedural",
        "usesSkyTextures" to (skyStyle == 7),
        "usesSkyArtwork" to false,
        "animationSpeed" to animationSpeed,
        "cameraReady" to cameraReady,
        "renderedThisFrame" to renderedThisFrame,
        "renderedFrames" to renderedFrames,
        "lastRenderedStyle" to lastStyle,
    )
}
