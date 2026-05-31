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
     * Called each frame from the world render pass ([gg.floyd.utils.render.RenderBatchManager]'s
     * RenderEvent.Last handler) with the live projection + camera-rotation modelview. The bob
     * transform is not captured here, so [tracerOrigin] returns null and tracers fall back to the
     * eye-based origin (the bob-stable tracer lock is a follow-up). [project] only needs
     * projection + modelView and works fully.
     */
    @JvmStatic
    fun capture(projection: Matrix4f, modelView: Matrix4f, cameraPos: Vec3) {
        this.projection = Matrix4f(projection)
        this.modelView = Matrix4f(modelView)
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
