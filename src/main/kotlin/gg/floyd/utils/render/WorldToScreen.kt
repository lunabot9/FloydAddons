package gg.floyd.utils.render

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Captures the per-frame world-render matrices so screen-space overlays and
 * view-bob-stable tracers can be derived from them outside of the world render pass.
 *
 * - [projection] is the projection matrix actually used for the frame. In 26.1.x vanilla
 *   builds it transiently as `cameraRenderState.projectionMatrix * bobPose`, so we rebuild
 *   that here from the captured camera state plus [bob].
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
     * Called each frame from [gg.floyd.mixin.mixins.LevelRendererMixin]. In 26.1.x the world pass
     * receives the camera rotation matrix directly while the bobbed projection only exists briefly in
     * `GameRenderer.renderLevel`, so we reconstruct the final projection from [CameraRenderState].
     */
    @JvmStatic
    fun capture(cameraRenderState: CameraRenderState, viewRotationMatrix: org.joml.Matrix4fc) {
        val proj = Matrix4f(cameraRenderState.projectionMatrix)
        bob?.let { proj.mul(it) }
        this.projection = proj
        this.modelView = Matrix4f(viewRotationMatrix)
        this.cameraPos = cameraRenderState.pos
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
    // Per-frame memo for the default-distance origin: capture()/captureBob() assign FRESH Matrix4f
    // instances each frame, so instance identity doubles as the frame stamp. Render-thread-only.
    // Block Search used to recompute this (two Matrix4f copies + inversions) per tracer per frame —
    // 10k times for one identical result.
    private var originMemoView: Matrix4f? = null
    private var originMemoBob: Matrix4f? = null
    private var originMemo: Vec3? = null

    fun tracerOrigin(distance: Float = 1f): Vec3? {
        val view = modelView ?: return null
        val bobMatrix = bob ?: return null
        if (distance == 1f && view === originMemoView && bobMatrix === originMemoBob) return originMemo
        val cam = cameraPos

        val eyePoint = Vector3f(0f, 0f, -distance)
        Matrix4f(bobMatrix).invert().transformPosition(eyePoint)
        Matrix4f(view).invert().transformPosition(eyePoint)

        val origin = Vec3(cam.x + eyePoint.x, cam.y + eyePoint.y, cam.z + eyePoint.z)
        if (distance == 1f) {
            originMemoView = view
            originMemoBob = bobMatrix
            originMemo = origin
        }
        return origin
    }

    /** The captured frame view matrix + camera for allocation-free batch math (tracer fans). */
    internal fun batchViewMatrix(): Matrix4f? = modelView
    internal fun batchCameraPos(): Vec3 = cameraPos

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
     * no change to the renderer.
     *
     * When [mirrorBehindCamera] is true, targets behind the camera are mirrored onto that same front depth
     * plane instead of being dropped. This keeps ESP tracers visible while you turn away from a target.
     */
    fun tracerTarget(worldPos: Vec3, distance: Float = 1f, mirrorBehindCamera: Boolean = false): Vec3? {
        val view = modelView ?: return null
        val cam = cameraPos

        val rel = Vector4f(
            (worldPos.x - cam.x).toFloat(),
            (worldPos.y - cam.y).toFloat(),
            (worldPos.z - cam.z).toFloat(),
            1f
        )
        view.transform(rel)
        if (rel.z > TRACER_VIEW_EPSILON) {
            if (!mirrorBehindCamera) return null
            return behindCameraTracerTarget(distance)
        }
        val t = tracerPlaneScale(rel.z, distance, mirrorBehindCamera = false) ?: return null
        return Vec3(
            cam.x + t * (worldPos.x - cam.x),
            cam.y + t * (worldPos.y - cam.y),
            cam.z + t * (worldPos.z - cam.z)
        )
    }

    /**
     * Scale factor that moves a camera->target ray onto the tracer depth plane. Negative [viewZ] values are
     * in front of the camera. When [mirrorBehindCamera] is enabled, positive [viewZ] values are reflected
     * through the camera so a stable on-screen direction still exists instead of dropping the tracer.
     */
    internal fun tracerPlaneScale(viewZ: Float, distance: Float = 1f, mirrorBehindCamera: Boolean = false): Float? {
        if (viewZ <= -TRACER_VIEW_EPSILON) return distance / -viewZ
        if (!mirrorBehindCamera || viewZ < TRACER_VIEW_EPSILON) return null
        return -distance / viewZ
    }

    /**
     * Center-bottom fallback for behind-camera tracers. This produces a stable point directly below the
     * crosshair on the same tracer depth plane so turning away from a target draws downward instead of up.
     */
    internal fun behindCameraTracerTarget(distance: Float = 1f): Vec3? =
        tracerPlaneScreenPoint(0f, TRACER_BEHIND_CAMERA_NDC_Y, distance)

    /**
     * World-space point on the tracer depth plane that projects to ([ndcX], [ndcY]). Uses the same clip
     * depth as [tracerOrigin], so the resulting line stays on the stable constant-width tracer plane.
     */
    internal fun tracerPlaneScreenPoint(ndcX: Float, ndcY: Float, distance: Float = 1f): Vec3? {
        val proj = projection ?: return null
        val view = modelView ?: return null
        val bobMatrix = bob ?: return null
        val cam = cameraPos

        val centerEye = Vector4f(0f, 0f, -distance, 1f)
        Matrix4f(bobMatrix).invert().transform(centerEye)

        val centerClip = Matrix4f(proj).transform(centerEye)
        if (kotlin.math.abs(centerClip.w) <= TRACER_VIEW_EPSILON) return null

        val desiredEye = Vector4f(
            ndcX * centerClip.w,
            ndcY * centerClip.w,
            centerClip.z,
            centerClip.w
        )
        Matrix4f(proj).invert().transform(desiredEye)
        if (kotlin.math.abs(desiredEye.w) <= TRACER_VIEW_EPSILON) return null
        desiredEye.div(desiredEye.w)

        val world = Vector3f(desiredEye.x, desiredEye.y, desiredEye.z)
        Matrix4f(view).invert().transformPosition(world)
        return Vec3(cam.x + world.x, cam.y + world.y, cam.z + world.z)
    }

    data class ScreenPos(val x: Float, val y: Float)

    private const val TRACER_VIEW_EPSILON = 1.0e-3f
    private const val TRACER_BEHIND_CAMERA_NDC_Y = -0.98f
}
