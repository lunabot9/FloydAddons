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
 *   Captured directly in [gg.floyd.mixin.mixins.GameRendererMixin] from the same
 *   `matrix4f.mul(poseStack.last().pose())` vanilla uses to bake the bob into the projection.
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
     * The bob transform is captured separately by [captureBob]; until the first frame composes it,
     * [tracerOrigin] returns null and tracers fall back to the eye-based origin. [project] only needs
     * projection + view and works without it.
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
     * Captures the composed view-bob / hurt-camera transform vanilla multiplies onto the projection
     * (`matrix4f.mul(poseStack.last().pose())` in `GameRenderer.renderLevel`). This is the eye-space
     * bob the frame's projection bakes in; [tracerOrigin] inverts it so the tracer start locks to
     * screen center regardless of bobbing. Defensive copy so the live vanilla matrix is never aliased.
     */
    @JvmStatic
    fun captureBob(bob: org.joml.Matrix4fc) {
        this.bob = Matrix4f(bob)
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
     * Gui-scaled screen pixels that one world block spans (vertically) at [pos] — i.e. the apparent
     * size scale for a billboard anchored there. Derived from the projection's vertical focal term
     * ([Matrix4f.m11]) and the point's eye-space depth (the perspective `w`), NOT by projecting a
     * world-axis offset.
     *
     * Projecting a vertical `+1` block offset (the old approach) foreshortens to ~0 screen pixels when
     * the camera looks up or down, and oscillates rapidly under view-bob while walking/jumping — which
     * made the overhead plate's size break when a target was above/below and flicker while moving. The
     * depth-based scale is independent of view pitch and far steadier under bob. Null if behind camera.
     */
    fun screenScale(pos: Vec3): Float? {
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
        // Strip the view-bob (baked into [projection]) from the depth so the apparent-size scale does
        // not oscillate as the camera bobs while walking/jumping. project() keeps the bob so the plate
        // still tracks the (bobbing) head; only the SIZE is held steady. Falls back to the bobbed
        // projection when the bob has not been captured yet.
        bob?.let { Matrix4f(it).invert().transform(clip) }
        proj.transform(clip)
        if (clip.w <= 0f) return null

        return 0.5f * mc.window.guiScaledHeight * kotlin.math.abs(proj.m11()) / clip.w
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

    /**
     * Re-aims a tracer's FAR endpoint onto the same camera-depth plane as [tracerOrigin] while keeping it
     * in the exact same screen direction as [worldPos]. The returned point lies on the ray from the camera
     * through [worldPos] (so it projects to the identical screen pixel as the real target — the tracer
     * looks unchanged), but at view-space depth `-distance`, the same depth as the origin.
     *
     * Why: a tracer drawn straight to a far player spans a huge depth range (origin ~1 block away, player
     * tens of blocks away). Its clip-space `w` therefore varies enormously along the segment, and the
     * screen-space line expansion produces a line that is thick at the near (crosshair) end and tapers to
     * nothing at the far (player) end. Pinning BOTH endpoints to the same depth plane makes the whole
     * segment constant-`w`, so its on-screen width is uniform — a true single-width 2D-looking line — with
     * no change to the renderer. Returns null if [worldPos] is at/behind the camera (caller falls back).
     */
    fun tracerTarget(worldPos: Vec3, distance: Float = 1f): Vec3? {
        val view = modelView ?: return null
        val cam = cameraPos

        val rel = Vector4f(
            (worldPos.x - cam.x).toFloat(),
            (worldPos.y - cam.y).toFloat(),
            (worldPos.z - cam.z).toFloat(),
            1f
        )
        view.transform(rel)
        val viewZ = rel.z                       // negative in front of the camera
        if (viewZ > -1.0e-3f) return null       // at/behind the camera

        val t = distance / -viewZ               // scale the camera->target ray to depth `distance`
        return Vec3(
            cam.x + t * (worldPos.x - cam.x),
            cam.y + t * (worldPos.y - cam.y),
            cam.z + t * (worldPos.z - cam.z)
        )
    }

    data class ScreenPos(val x: Float, val y: Float)
}
