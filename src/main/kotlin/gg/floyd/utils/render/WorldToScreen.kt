package gg.floyd.utils.render

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Captures the per-frame world-render matrices (set in [GameRendererMixin] during
 * `renderLevel`) so screen-space overlays and view-bob-stable tracers can be derived
 * from them outside of the world render pass.
 *
 * - [projection] is the projection matrix actually used for the frame (it already
 *   includes the view-bob transform baked into it by vanilla).
 * - [bob] is the composed view-bob / hurt-camera transform vanilla multiplied onto the
 *   projection. It is identity-equivalent when bobbing is disabled and no hurt is active.
 * - [modelView] is the camera-rotation modelview (no bobbing).
 * - [cameraPos] is the interpolated camera position.
 */
object WorldToScreen {
    @Volatile private var projection: Matrix4f? = null
    @Volatile private var bob: Matrix4f? = null
    @Volatile private var modelView: Matrix4f? = null
    @Volatile private var cameraPos: Vec3 = Vec3.ZERO

    /**
     * Called each frame from [gg.floyd.mixin.mixins.LevelRendererMixin] with the three Matrix4f
     * parameters of `LevelRenderer.renderLevel` (the exact matrices the GPU renders the world with)
     * plus the camera position. The projection is identified by its perspective structure (m33 == 0,
     * the perspective-divide row); the view matrix is the first remaining (affine) matrix. This makes
     * the identification independent of the renderLevel parameter order.
     *
     * The bob transform is not captured, so [tracerOrigin] returns null and tracers fall back to the
     * eye-based origin (the bob-stable tracer lock is a follow-up). [project] only needs projection +
     * view and works fully.
     */
    @JvmStatic
    fun capture(m1: Matrix4f, m2: Matrix4f, m3: Matrix4f, cameraPos: Vec3) {
        val mats = arrayOf(m1, m2, m3)
        val proj = mats.firstOrNull { kotlin.math.abs(it.m33()) < 1.0e-4f } ?: return
        val view = mats.firstOrNull { it !== proj } ?: return
        this.projection = Matrix4f(proj)
        this.modelView = Matrix4f(view)
        this.cameraPos = cameraPos
    }

    /**
     * Projects a world position to gui-scaled screen coordinates, or null if the point
     * is behind the camera. The result is stable under view bobbing because the projected
     * point and the bob travel together through the same matrices.
     */
    fun project(pos: Vec3): ScreenPos? {
        val proj = projection ?: return null
        val view = modelView ?: return null
        val cam = cameraPos

        val clip = Vector4f(
            (pos.x - cam.x).toFloat(),
            (pos.y - cam.y).toFloat(),
            (pos.z - cam.z).toFloat(),
            1f
        )
        view.transform(clip)
        proj.transform(clip)

        if (clip.w <= 0f) return null

        val ndcX = clip.x / clip.w
        val ndcY = clip.y / clip.w

        val width = mc.window.guiScaledWidth
        val height = mc.window.guiScaledHeight
        return ScreenPos(
            (ndcX * 0.5f + 0.5f) * width,
            (1f - (ndcY * 0.5f + 0.5f)) * height
        )
    }

    /**
     * World-space start point for a tracer so that, after the frame's (bobbed) projection,
     * it lands exactly on screen center / the crosshair regardless of view bobbing.
     *
     * Derivation: a point on the projection center axis in eye space is `(0,0,-distance)`.
     * The frame multiplies the bob onto the projection, so the eye-space point that maps to
     * center is `bob^-1 * (0,0,-distance)`. Transforming that back through the inverse
     * modelview and adding the camera position yields the stable world-space origin.
     */
    fun tracerOrigin(distance: Float = 1f): Vec3? {
        val view = modelView ?: return null
        val bobMatrix = bob ?: return null
        val cam = cameraPos

        val eyePoint = Vector3f(0f, 0f, -distance)
        Matrix4f(bobMatrix).invert().transformPosition(eyePoint)
        Matrix4f(view).invert().transformPosition(eyePoint)

        return Vec3(cam.x + eyePoint.x, cam.y + eyePoint.y, cam.z + eyePoint.z)
    }

    data class ScreenPos(val x: Float, val y: Float)
}
