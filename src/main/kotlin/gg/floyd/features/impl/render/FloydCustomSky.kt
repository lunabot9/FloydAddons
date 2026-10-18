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
    description = "Six animated 360-degree skies, from auroras and nebulae to an eclipse and rose clouds.",
) {
    private val styles = listOf("Golden Aurora", "Nebula", "Emerald Aurora", "Crimson Eclipse", "Sapphire Galaxy", "Rose Clouds")
    private val skyStyle by SelectorSetting("Sky Style", "Golden Aurora", styles, desc = "Choose the animated sky surrounding the world. Golden Aurora matches the main menu.")
    private val animationSpeed by NumberSetting("Animation Speed", 1f, .1f, 3f, .1f, desc = "Speed of the sky animation. Golden Aurora also follows your menu background settings.")
    private val qualities = listOf("Performance", "Balanced", "High")
    private val quality by SelectorSetting("Render Quality", "Balanced", qualities, desc = "Balanced renders volumetric clouds and nebulae at half resolution with sharp native-resolution stars. Lighter skies remain native. High uses native resolution throughout.")
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
        "gpuProfile" to SkyGpuTimer.state(),
        "renderer" to "procedural",
        "usesSkyTextures" to false,
        "animationSpeed" to animationSpeed,
        "cameraReady" to cameraReady,
        "renderedThisFrame" to renderedThisFrame,
        "renderedFrames" to renderedFrames,
        "lastRenderedStyle" to lastStyle,
    )
}
