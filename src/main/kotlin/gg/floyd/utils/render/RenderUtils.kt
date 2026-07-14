package gg.floyd.utils.render

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import gg.floyd.mixin.accessors.BeaconBeamAccessor
import gg.floyd.mixin.accessors.GameRendererFogAccessor
import net.minecraft.client.renderer.fog.FogRenderer
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.events.RenderEvent
import gg.floyd.events.core.on
import gg.floyd.features.impl.misc.FloydCompatibility
import gg.floyd.utils.ui.rendering.PostHudOverlay
import gg.floyd.utils.Color
import gg.floyd.utils.Color.Companion.blue
import gg.floyd.utils.Color.Companion.green
import gg.floyd.utils.Color.Companion.multiplyAlpha
import gg.floyd.utils.Color.Companion.red
import gg.floyd.utils.addVec
import gg.floyd.utils.renderPos
import gg.floyd.utils.unaryMinus
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/beacon_beam.png")

/**
 * The GameRenderer's "no fog" uniform buffer ([FogRenderer.FogMode.NONE]), used to draw ESP geometry
 * unaffected by world fog (blindness / darkness / distance). Null if the accessor is unavailable, in
 * which case the ESP simply renders with the current fog (its prior behavior).
 */
private fun noFogBuffer(): GpuBufferSlice? = runCatching {
    (mc.gameRenderer as GameRendererFogAccessor).`floydaddons$getFogRenderer`().getBuffer(FogRenderer.FogMode.NONE)
}.getOrNull()

internal data class LineData(val from: Vec3, val to: Vec3, val color1: Int, val color2: Int, val thickness: Float, val depth: Boolean)
internal data class BoxData(val aabb: AABB, val r: Float, val g: Float, val b: Float, val a: Float, val thickness: Float, val depth: Boolean)

/**
 * One shared-style batch of boxes queued as a SINGLE record (Block Search queues 10k boxes/frame —
 * per-box BoxData records were ~62MB/s of garbage). The queuing side must treat [aabbs] as frozen
 * once queued: a record can survive into the next frame when the Last flush skips (non-BufferSource
 * consumers), so producers hand over a fresh list per rebuild, never mutate a queued one.
 */
internal class BoxBatchData(val aabbs: List<AABB>, val r: Float, val g: Float, val b: Float, val a: Float, val thickness: Float, val depth: Boolean)

/**
 * One crosshair-anchored tracer fan queued as a single record. The flush computes the shared
 * origin ONCE and re-aims every target onto the origin's depth plane with scratch math —
 * allocation-free per line (the per-tracer drawTracer path costs ~5 allocations per line per
 * frame, ~2MB/frame at Block Search's 10k cap). Same frozen-list contract as [BoxBatchData].
 * [mirrorBehindCamera] keeps tracers visible for behind-camera targets by mirroring them onto the
 * front depth plane instead of dropping them.
 */
internal class TracerFanData(
    val targets: List<Vec3>,
    val color: Int,
    val thickness: Float,
    val depth: Boolean,
    val mirrorBehindCamera: Boolean
)
internal data class BeaconData(val pos: BlockPos, val color: Color, val isScoping: Boolean, val gameTime: Long)
internal data class TextData(val text: String, val pos: Vec3, val scale: Float, val depth: Boolean, val cameraRotation: org.joml.Quaternionf, val font: Font, val textWidth: Float)
internal data class TexturedQuadData(val texture: Identifier, val bl: Vec3, val tl: Vec3, val tr: Vec3, val br: Vec3, val nx: Float, val ny: Float, val nz: Float, val color: Int, val depth: Boolean)

class RenderConsumer {
    internal val lines = ObjectArrayList<LineData>()
    internal val filledBoxes = ObjectArrayList<BoxData>()
    internal val wireBoxes = ObjectArrayList<BoxData>()
    internal val wireBoxBatches = ObjectArrayList<BoxBatchData>()
    internal val filledBoxBatches = ObjectArrayList<BoxBatchData>()
    internal val tracerFans = ObjectArrayList<TracerFanData>()

    internal val beaconBeams = ObjectArrayList<BeaconData>()
    internal val texts = ObjectArrayList<TextData>()
    internal val texturedQuads = ObjectArrayList<TexturedQuadData>()

    fun clear() {
        lines.clear()
        filledBoxes.clear()
        wireBoxes.clear()
        wireBoxBatches.clear()
        filledBoxBatches.clear()
        tracerFans.clear()
        beaconBeams.clear()
        texts.clear()
        texturedQuads.clear()
    }
}

object RenderBatchManager {
    val renderConsumer = RenderConsumer()

    // Last-flushed-frame counts for the bridge: /state's callClient lands BETWEEN frames, after
    // the per-frame clear(), so the live "queued" sizes read 0 structurally. These are captured
    // at flush time (volatile publication for the HTTP threads) and are the numbers e2e
    // verification should assert on.
    @Volatile private var lastFlushedCounts: Map<String, Int> = emptyMap()

    internal fun shouldRenderWorldOverlayPass(safeHudLayer: Boolean): Boolean = !safeHudLayer

    fun state(): Map<String, Any?> = mapOf(
        "worldOverlayPassEnabled" to shouldRenderWorldOverlayPass(FloydCompatibility.shouldUseSafeHudLayer()),
        "queued" to mapOf(
            "lines" to renderConsumer.lines.size,
            "filledBoxes" to renderConsumer.filledBoxes.size,
            "wireBoxes" to renderConsumer.wireBoxes.size,
            "wireBoxBatches" to renderConsumer.wireBoxBatches.size,
            "beaconBeams" to renderConsumer.beaconBeams.size,
            "texts" to renderConsumer.texts.size,
            "texturedQuads" to renderConsumer.texturedQuads.size
        ),
        "lastFlushed" to lastFlushedCounts
    )

    init {
        on<RenderEvent.Last> {
            if (!shouldRenderWorldOverlayPass(FloydCompatibility.shouldUseSafeHudLayer())) {
                if (lastFlushedCounts.isNotEmpty()) lastFlushedCounts = emptyMap()
                renderConsumer.clear()
                RoundRectPIPRenderer.clear()
                PanelBlurPIPRenderer.clear()
                PooledPicturePIPRenderer.recycleAll()
                return@on
            }
            val matrix = context.poseStack()
            val bufferSource = context.bufferSource()
            val camera = mc.gameRenderer.mainCamera.position()

            // Draw the ESP world geometry (tracers, boxes, nameplate text) with fog DISABLED so
            // blindness / darkness / distance fog never tints far-away players' ESP toward the (often
            // black) fog color. We bind the "no fog" buffer, flush the ESP vertices ourselves, then
            // restore the world's own fog so nothing else is affected.
            val savedFog = RenderSystem.getShaderFog()
            val noFog = noFogBuffer()
            if (noFog != null) RenderSystem.setShaderFog(noFog)

            matrix.pushPose()
            matrix.translate(-camera.x, -camera.y, -camera.z)

            matrix.renderQueuedLinesAndWireBoxes(renderConsumer.lines, renderConsumer.wireBoxes, renderConsumer.wireBoxBatches, bufferSource)
            matrix.renderQueuedTracerFans(renderConsumer.tracerFans, bufferSource)
            matrix.renderQueuedFilledBoxes(renderConsumer.filledBoxes, renderConsumer.filledBoxBatches, bufferSource)
            matrix.renderQueuedTexturedQuads(renderConsumer.texturedQuads, bufferSource)
            matrix.popPose()

            matrix.renderQueuedBeaconBeams(renderConsumer.beaconBeams, camera)
            matrix.renderQueuedTexts(renderConsumer.texts, bufferSource, camera)

            // World-space ESP overhead billboards (nameplate panel + health + equipment icons) — drawn in
            // this same no-fog world pass so the GPU perspective sizes them like vanilla nametags (the
            // entire fix for the old screen-scale "starts huge then shrinks"). `matrix` is the base
            // (identity-model) world PoseStack after the popPose above; health text queues into bufferSource
            // (flushed at the endBatch below), the SDF rect + item icons flush themselves.
            val cameraRotation = mc.gameRenderer.mainCamera.rotation()
            gg.floyd.features.impl.pvp.FloydPlayerEsp.renderOverheadBillboard(matrix, camera, cameraRotation, bufferSource)

            if (noFog != null) {
                bufferSource.endBatch()
                if (savedFog != null) RenderSystem.setShaderFog(savedFog)
            }
            if (renderConsumer.lines.isNotEmpty() || renderConsumer.wireBoxes.isNotEmpty() ||
                renderConsumer.wireBoxBatches.isNotEmpty() || renderConsumer.filledBoxBatches.isNotEmpty() ||
                renderConsumer.tracerFans.isNotEmpty() || renderConsumer.filledBoxes.isNotEmpty()
            ) {
                lastFlushedCounts = mapOf(
                    "lines" to renderConsumer.lines.size,
                    "wireBoxes" to renderConsumer.wireBoxes.size,
                    "filledBoxes" to renderConsumer.filledBoxes.size,
                    "wireBoxBatchBoxes" to renderConsumer.wireBoxBatches.sumOf { it.aabbs.size },
                    "filledBoxBatchBoxes" to renderConsumer.filledBoxBatches.sumOf { it.aabbs.size },
                    "tracerFanTargets" to renderConsumer.tracerFans.sumOf { it.targets.size },
                    "texts" to renderConsumer.texts.size,
                )
            } else if (lastFlushedCounts.isNotEmpty()) {
                lastFlushedCounts = emptyMap()
            }
            renderConsumer.clear()

            RoundRectPIPRenderer.clear()
            PanelBlurPIPRenderer.clear()
            // Recycle the pooled per-panel PIP textures now that the prior frame's GUI flush has drawn
            // every blit, so this frame's panels each get their own live texture (fixes overlapping
            // Floyd panels flickering / going black from the single shared vanilla PIP texture).
            PooledPicturePIPRenderer.recycleAll()

            // Sole owner of the direct Floyd HUD compositor on 26.1.2. The later GameRenderer
            // GuiRenderState.reset seam no longer exists reliably; keeping this at END_MAIN makes
            // Inventory HUD, Day Tracker and Custom Scoreboard deterministic without double draws.
            if (!FloydCompatibility.shouldUseSafeHudLayer()) {
                PostHudOverlay.render()
            }

            // NOTE: Floyd's no-PIP HUD panels are NOT drawn here. END_MAIN fires before the first-person
            // hand item is rendered (renderItemInHand runs later inside renderLevel/GameRenderer), so drawing
            // here would put the held item ON TOP of the HUD panels. The panel pass now runs from
            // GameRendererMixin at GuiRenderState.reset — after the world+hand, before the vanilla HUD/screen.
        }
    }
}

private fun Int.isFullyOpaque(): Boolean = ((this ushr 24) and 0xFF) == 0xFF

private fun resolveLineRenderType(depth: Boolean, fullyOpaque: Boolean) = when {
    depth && fullyOpaque -> RenderTypes.LINES
    depth -> RenderTypes.LINES_TRANSLUCENT
    // No-depth ESP lines (tracers + wireframes) go through the antialiased line type for smooth, soft
    // edges. It uses translucent blend, so it serves both the opaque- and translucent-color cases.
    else -> CustomRenderType.LINES_AA_ESP
}

private fun LineData.renderType() = resolveLineRenderType(
    depth = depth,
    fullyOpaque = color1.isFullyOpaque() && color2.isFullyOpaque()
)

private fun BoxData.lineRenderType() = resolveLineRenderType(
    depth = depth,
    fullyOpaque = a >= 0.999f
)

private fun BoxData.filledRenderType() = if (depth) RenderTypes.debugFilledBox() else CustomRenderType.QUADS_ESP

fun RenderEvent.Extract.drawTexturedQuad(
    texture: Identifier,
    pos: Vec3,
    width: Float,
    height: Float,
    yaw: Float = 0f,
    color: Color = Color(255, 255, 255),
    depth: Boolean = true
) {
    val yawRad = Math.toRadians(yaw.toDouble())
    val rx = cos(yawRad).toFloat()
    val rz = sin(yawRad).toFloat()
    val hw = width  * 0.5
    val hh = height * 0.5

    // right = (rx, 0, rz), up = (0, 1, 0), normal = cross(right, up) = (-rz, 0, rx)
    val bl = Vec3(pos.x - rx * hw, pos.y - hh, pos.z - rz * hw)
    val tl = Vec3(pos.x - rx * hw, pos.y + hh, pos.z - rz * hw)
    val tr = Vec3(pos.x + rx * hw, pos.y + hh, pos.z + rz * hw)
    val br = Vec3(pos.x + rx * hw, pos.y - hh, pos.z + rz * hw)

    consumer.texturedQuads.add(TexturedQuadData(texture, bl, tl, tr, br, -rz, 0f, rx, color.rgba, depth))
}

private fun PoseStack.renderQueuedTexturedQuads(
    quads: List<TexturedQuadData>,
    bufferSource: MultiBufferSource.BufferSource
) {
    if (quads.isEmpty()) return
    val last = this.last()

    for (quad in quads) {
        val buffer = bufferSource.getBuffer(RenderTypes.entityCutoutZOffset(quad.texture))

        fun vertex(p: Vec3, u: Float, v: Float) {
            buffer.addVertex(last, p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
                .setColor(quad.color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setUv2(LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT)
                .setNormal(last, quad.nx, quad.ny, quad.nz)
        }

        vertex(quad.bl, 0f, 1f)
        vertex(quad.tl, 0f, 0f)
        vertex(quad.tr, 1f, 0f)
        vertex(quad.br, 1f, 1f)
    }
}

private fun PoseStack.renderQueuedLinesAndWireBoxes(
    lines: List<LineData>,
    wireBoxes: List<BoxData>,
    wireBoxBatches: List<BoxBatchData>,
    bufferSource: MultiBufferSource.BufferSource
) {
    if (lines.isEmpty() && wireBoxes.isEmpty() && wireBoxBatches.isEmpty()) return
    val last = this.last()

    for (line in lines) {
        val dirX = line.to.x - line.from.x
        val dirY = line.to.y - line.from.y
        val dirZ = line.to.z - line.from.z
        val buffer = bufferSource.getBuffer(line.renderType())

        PrimitiveRenderer.renderVector(
            last, buffer,
            Vector3f(line.from.x.toFloat(), line.from.y.toFloat(), line.from.z.toFloat()),
            Vec3(dirX, dirY, dirZ),
            line.color1, line.color2, line.thickness
        )
    }

    for (box in wireBoxes) {
        val buffer = bufferSource.getBuffer(box.lineRenderType())
        PrimitiveRenderer.renderLineBox(
            last, buffer, box.aabb,
            box.r, box.g, box.b, box.a, box.thickness
        )
    }

    for (batch in wireBoxBatches) {
        // Same render-type resolution as single wire boxes; buffer hoisted once per batch.
        val buffer = bufferSource.getBuffer(resolveLineRenderType(batch.depth, batch.a >= 0.999f))
        val aabbs = batch.aabbs
        for (i in aabbs.indices) {
            PrimitiveRenderer.renderLineBox(last, buffer, aabbs[i], batch.r, batch.g, batch.b, batch.a, batch.thickness)
        }
    }
}

// Scratch for the tracer-fan flush — render-thread-only, non-reentrant (the sole flush site is
// RenderBatchManager's RenderEvent.Last pass).
private val tracerFanScratch = Vector4f()

/**
 * Flushes queued tracer fans: the shared crosshair origin is resolved once per fan, then every
 * target is re-aimed onto the origin's depth plane (the exact [WorldToScreen.tracerTarget] math,
 * inlined against [tracerFanScratch] so the loop is allocation-free) and emitted as one line.
 * When [TracerFanData.mirrorBehindCamera] is false, targets at/behind the camera are skipped.
 * When the frame matrices are not captured yet, falls back to the eye-anchored origin with
 * straight lines to the real targets, mirroring [drawTracer]'s fallback.
 */
private fun PoseStack.renderQueuedTracerFans(fans: List<TracerFanData>, bufferSource: MultiBufferSource.BufferSource) {
    if (fans.isEmpty()) return
    val last = this.last()

    for (fan in fans) {
        if (fan.targets.isEmpty()) continue
        val buffer = bufferSource.getBuffer(resolveLineRenderType(fan.depth, fan.color.isFullyOpaque()))
        val origin = WorldToScreen.tracerOrigin()
        val view = WorldToScreen.batchViewMatrix()
        val targets = fan.targets
        if (origin != null && view != null) {
            val cam = WorldToScreen.batchCameraPos()
            val ox = origin.x.toFloat()
            val oy = origin.y.toFloat()
            val oz = origin.z.toFloat()
            val behindCameraTarget = if (fan.mirrorBehindCamera) WorldToScreen.behindCameraTracerTarget() else null
            for (i in targets.indices) {
                val t = targets[i]
                val relX = t.x - cam.x
                val relY = t.y - cam.y
                val relZ = t.z - cam.z
                tracerFanScratch.set(relX.toFloat(), relY.toFloat(), relZ.toFloat(), 1f)
                view.transform(tracerFanScratch)
                if (tracerFanScratch.z > 1.0e-3f) {
                    val fallback = behindCameraTarget ?: continue
                    PrimitiveRenderer.renderLine(
                        last, buffer,
                        ox, oy, oz,
                        fallback.x.toFloat(), fallback.y.toFloat(), fallback.z.toFloat(),
                        fan.color, fan.thickness
                    )
                    continue
                }
                val s = WorldToScreen.tracerPlaneScale(tracerFanScratch.z) ?: continue
                PrimitiveRenderer.renderLine(
                    last, buffer,
                    ox, oy, oz,
                    (cam.x + s * relX).toFloat(), (cam.y + s * relY).toFloat(), (cam.z + s * relZ).toFloat(),
                    fan.color, fan.thickness
                )
            }
        } else {
            val player = mc.player ?: continue
            val from = player.renderPos.add(player.forward.add(0.0, player.eyeHeight.toDouble(), 0.0))
            val fx = from.x.toFloat()
            val fy = from.y.toFloat()
            val fz = from.z.toFloat()
            for (i in targets.indices) {
                val t = targets[i]
                PrimitiveRenderer.renderLine(last, buffer, fx, fy, fz, t.x.toFloat(), t.y.toFloat(), t.z.toFloat(), fan.color, fan.thickness)
            }
        }
    }
}

private fun PoseStack.renderQueuedFilledBoxes(
    consumer: List<BoxData>,
    batches: List<BoxBatchData>,
    bufferSource: MultiBufferSource.BufferSource
) {
    if (consumer.isEmpty() && batches.isEmpty()) return
    val last = this.last()

    for (box in consumer) {
        val buffer = bufferSource.getBuffer(box.filledRenderType())
        PrimitiveRenderer.addChainedFilledBoxVertices(
            last, buffer,
            box.aabb.minX.toFloat(), box.aabb.minY.toFloat(), box.aabb.minZ.toFloat(),
            box.aabb.maxX.toFloat(), box.aabb.maxY.toFloat(), box.aabb.maxZ.toFloat(),
            box.r, box.g, box.b, box.a
        )
    }

    for (batch in batches) {
        val buffer = bufferSource.getBuffer(if (batch.depth) RenderTypes.debugFilledBox() else CustomRenderType.QUADS_ESP)
        val aabbs = batch.aabbs
        for (i in aabbs.indices) {
            val aabb = aabbs[i]
            PrimitiveRenderer.addChainedFilledBoxVertices(
                last, buffer,
                aabb.minX.toFloat(), aabb.minY.toFloat(), aabb.minZ.toFloat(),
                aabb.maxX.toFloat(), aabb.maxY.toFloat(), aabb.maxZ.toFloat(),
                batch.r, batch.g, batch.b, batch.a
            )
        }
    }
}

private fun PoseStack.renderQueuedBeaconBeams(consumer: List<BeaconData>, camera: Vec3) {
    for (beacon in consumer) {
        pushPose()
        translate(beacon.pos.x - camera.x, beacon.pos.y - camera.y, beacon.pos.z - camera.z)

        val centerX = beacon.pos.x + 0.5
        val centerZ = beacon.pos.z + 0.5
        val dx = camera.x - centerX
        val dz = camera.z - centerZ
        val length = sqrt(dx * dx + dz * dz).toFloat()

        val scale = if (beacon.isScoping) 1.0f else maxOf(1.0f, length * 0.010416667f)

        BeaconBeamAccessor.invokeRenderBeam(
            this,
            mc.gameRenderer.featureRenderDispatcher.submitNodeStorage,
            BEAM_TEXTURE,
            1f,
            beacon.gameTime.toFloat(),
            0,
            319,
            beacon.color.rgba,
            0.2f * scale,
            0.25f * scale
        )
        popPose()
    }
}

private fun PoseStack.renderQueuedTexts(consumer: List<TextData>, bufferSource: MultiBufferSource.BufferSource, camera: Vec3) {
    val cameraPos = -camera

    for (textData in consumer) {
        pushPose()
        val pose = last().pose()
        val scaleFactor = textData.scale * 0.025f

        pose.translate(textData.pos.toVector3f())
            .translate(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())
            .rotate(textData.cameraRotation)
            .scale(scaleFactor, -scaleFactor, scaleFactor)

        textData.font.drawInBatch(
            textData.text, -textData.textWidth / 2f, 0f, -1, true, pose, bufferSource,
            if (textData.depth) Font.DisplayMode.POLYGON_OFFSET else Font.DisplayMode.SEE_THROUGH,
            0, LightTexture.FULL_BRIGHT
        )

        popPose()
    }
}

fun RenderEvent.Extract.drawTracer(
    to: Vec3,
    color: Color,
    depth: Boolean,
    thickness: Float = 3f,
    mirrorBehindCamera: Boolean = false
) {
    if (mc.player == null) return
    // Lock the origin to the crosshair / screen-center so the tracer does not wobble with
    // view bobbing. Fall back to the eye position if the render matrices are unavailable.
    val origin = WorldToScreen.tracerOrigin()
    if (origin != null) {
        // Matrices ready: aim the far endpoint onto the origin's DEPTH PLANE (same screen direction as
        // `to`). Both endpoints then sit at the same camera depth, so the whole tracer is constant-depth
        // -> uniform on-screen width (no taper) AND it never crosses the camera/near plane (fixing the
        // "tracer vanishes/glitches as it passes through the camera"). Behind-camera targets can be
        // optionally mirrored onto the front depth plane so ESP tracers stay visible while turning away.
        val target = WorldToScreen.tracerTarget(to, mirrorBehindCamera = mirrorBehindCamera) ?: return
        drawLine(listOf(origin, target), color, depth, thickness)
        return
    }
    // Matrices not captured yet (first frames): fall back to an eye-anchored origin and the real target.
    val from = mc.player?.let { it.renderPos.add(it.forward.add(0.0, it.eyeHeight.toDouble(), 0.0)) } ?: return
    drawLine(listOf(from, to), color, depth, thickness)
}

fun RenderEvent.Extract.drawLine(points: Collection<Vec3>, color: Color, depth: Boolean, thickness: Float = 3f) {
    drawLine(points, color, color, depth, thickness)
}

fun RenderEvent.Extract.drawLine(points: Collection<Vec3>, color1: Color, color2: Color, depth: Boolean, thickness: Float = 3f) {
    if (points.size < 2) return

    val rgba1 = color1.rgba
    val rgba2 = color2.rgba

    val iterator = points.iterator()
    var current = iterator.next()

    while (iterator.hasNext()) {
        val next = iterator.next()
        consumer.lines.add(LineData(current, next, rgba1, rgba2, thickness, depth))
        current = next
    }
}

fun RenderEvent.Extract.drawWireFrameBox(aabb: AABB, color: Color, thickness: Float = 3f, depth: Boolean = false) {
    // Read the (possibly chroma) rgba once so all three channels come from the same hue and we
    // do a single cache lookup instead of three.
    val rgba = color.rgba
    consumer.wireBoxes.add(
        BoxData(aabb, rgba.red / 255f, rgba.green / 255f, rgba.blue / 255f, color.alphaFloat, thickness, depth)
    )
}

fun RenderEvent.Extract.drawFilledBox(aabb: AABB, color: Color, depth: Boolean = false) {
    val rgba = color.rgba
    consumer.filledBoxes.add(
        BoxData(aabb, rgba.red / 255f, rgba.green / 255f, rgba.blue / 255f, color.alphaFloat, 3f, depth)
    )
}

fun RenderEvent.Extract.drawStyledBox(
    aabb: AABB,
    color: Color,
    style: Int = 0,
    depth: Boolean = true
) {
    when (style) {
        0 -> drawFilledBox(aabb, color, depth = depth)
        1 -> drawWireFrameBox(aabb, color, depth = depth)
        2 -> {
            drawFilledBox(aabb, color.multiplyAlpha(0.5f), depth = depth)
            drawWireFrameBox(aabb, color, depth = depth)
        }
    }
}

/**
 * Batch variant of [drawStyledBox]: queues ALL of [aabbs] as one or two records (style 2 = filled
 * at half alpha + wire at full alpha, matching the single-box path exactly — the alpha is halved
 * as a float, not via Color.multiplyAlpha, so the chroma hue keeps animating per frame). The
 * caller must NOT mutate [aabbs] after queueing (see [BoxBatchData]); hand over a fresh list per
 * rebuild and reuse it across frames while unchanged.
 */
fun RenderEvent.Extract.drawStyledBoxBatch(
    aabbs: List<AABB>,
    color: Color,
    style: Int = 0,
    depth: Boolean = true,
    thickness: Float = 3f
) {
    if (aabbs.isEmpty()) return
    val rgba = color.rgba
    val r = rgba.red / 255f
    val g = rgba.green / 255f
    val b = rgba.blue / 255f
    val a = color.alphaFloat
    when (style) {
        0 -> consumer.filledBoxBatches.add(BoxBatchData(aabbs, r, g, b, a, thickness, depth))
        1 -> consumer.wireBoxBatches.add(BoxBatchData(aabbs, r, g, b, a, thickness, depth))
        2 -> {
            consumer.filledBoxBatches.add(BoxBatchData(aabbs, r, g, b, a * 0.5f, thickness, depth))
            consumer.wireBoxBatches.add(BoxBatchData(aabbs, r, g, b, a, thickness, depth))
        }
    }
}

/**
 * Batch variant of [drawTracer]: queues ALL of [targets] as one record; the flush resolves the
 * crosshair origin once and re-aims each target with scratch math (see [TracerFanData]). The
 * caller must NOT mutate [targets] after queueing — hand over a fresh list per rebuild.
 */
fun RenderEvent.Extract.drawTracerFan(
    targets: List<Vec3>,
    color: Color,
    thickness: Float = 3f,
    depth: Boolean = false,
    mirrorBehindCamera: Boolean = false
) {
    if (targets.isEmpty()) return
    consumer.tracerFans.add(TracerFanData(targets, color.rgba, thickness, depth, mirrorBehindCamera))
}

fun RenderEvent.Extract.drawBeaconBeam(position: BlockPos, color: Color) {
    val isScoping = mc.player?.isScoping == true
    val gameTime = mc.level?.gameTime ?: 0L

    consumer.beaconBeams.add(BeaconData(position, color, isScoping, gameTime))
}

fun RenderEvent.Extract.drawText(text: String, pos: Vec3, scale: Float, depth: Boolean) {
    val cameraRotation = mc.gameRenderer.mainCamera.rotation()
    val font = mc.font
    val textWidth = font.width(text).toFloat()

    consumer.texts.add(TextData(text, pos, scale, depth, cameraRotation, font, textWidth))
}

fun RenderEvent.Extract.drawCustomBeacon(
    title: String,
    position: BlockPos,
    color: Color,
    increase: Boolean = true,
    distance: Boolean = true
) {
    val dist = mc.player?.blockPosition()?.distManhattan(position) ?: return

    drawWireFrameBox(AABB(position), color, depth = false)
    drawBeaconBeam(position, color)
    drawText(
        (if (distance) ("$title §r§f(§3${dist}m§f)") else title),
        position.center.addVec(y = 1.7),
        if (increase) max(1f, dist * 0.05f) else 2f,
        false
    )
}

fun RenderEvent.Extract.drawCylinder(
    center: Vec3,
    radius: Float,
    height: Float,
    color: Color,
    segments: Int = 32,
    thickness: Float = 5f,
    depth: Boolean = false
) {
    val angleStep = 2.0 * Math.PI / segments
    val rgba = color.rgba

    for (i in 0 until segments) {
        val angle1 = i * angleStep
        val angle2 = (i + 1) * angleStep

        val x1 = (radius * cos(angle1)).toFloat()
        val z1 = (radius * sin(angle1)).toFloat()
        val x2 = (radius * cos(angle2)).toFloat()
        val z2 = (radius * sin(angle2)).toFloat()

        val p1Top = center.add(x1.toDouble(), height.toDouble(), z1.toDouble())
        val p2Top = center.add(x2.toDouble(), height.toDouble(), z2.toDouble())
        val p1Bottom = center.add(x1.toDouble(), 0.0, z1.toDouble())
        val p2Bottom = center.add(x2.toDouble(), 0.0, z2.toDouble())

        consumer.lines.add(LineData(p1Top, p2Top, rgba, rgba, thickness, depth))
        consumer.lines.add(LineData(p1Bottom, p2Bottom, rgba, rgba, thickness, depth))
        consumer.lines.add(LineData(p1Bottom, p1Top, rgba, rgba, thickness, depth))
    }
}

object PrimitiveRenderer {

    private val edges = intArrayOf(
        0, 1,  1, 5,  5, 4,  4, 0,
        3, 2,  2, 6,  6, 7,  7, 3,
        0, 3,  1, 2,  5, 6,  4, 7
    )

    // Scratch corners reused across calls — renderLineBox is render-thread-only and non-reentrant
    // (sole flush site is RenderBatchManager's RenderEvent.Last pass). Was a floatArrayOf per call:
    // at Block Search's 10k boxes/frame that alone was ~120MB/s of garbage.
    private val cornersScratch = FloatArray(24)

    fun renderLineBox(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        aabb: AABB,
        r: Float, g: Float, b: Float, a: Float,
        thickness: Float
    ) {
        val x0 = aabb.minX.toFloat()
        val y0 = aabb.minY.toFloat()
        val z0 = aabb.minZ.toFloat()
        val x1 = aabb.maxX.toFloat()
        val y1 = aabb.maxY.toFloat()
        val z1 = aabb.maxZ.toFloat()

        val corners = cornersScratch
        corners[0] = x0; corners[1] = y0; corners[2] = z0
        corners[3] = x1; corners[4] = y0; corners[5] = z0
        corners[6] = x1; corners[7] = y1; corners[8] = z0
        corners[9] = x0; corners[10] = y1; corners[11] = z0
        corners[12] = x0; corners[13] = y0; corners[14] = z1
        corners[15] = x1; corners[16] = y0; corners[17] = z1
        corners[18] = x1; corners[19] = y1; corners[20] = z1
        corners[21] = x0; corners[22] = y1; corners[23] = z1

        for (i in edges.indices step 2) {
            val i0 = edges[i] * 3
            val i1 = edges[i + 1] * 3

            val x0 = corners[i0]
            val y0 = corners[i0 + 1]
            val z0 = corners[i0 + 2]
            val x1 = corners[i1]
            val y1 = corners[i1 + 1]
            val z1 = corners[i1 + 2]

            val dx = x1 - x0
            val dy = y1 - y0
            val dz = z1 - z0

            buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(thickness)
            buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(thickness)
        }
    }

    fun addChainedFilledBoxVertices(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val matrix = pose.pose()

        fun vertex(x: Float, y: Float, z: Float) {
            buffer.addVertex(matrix, x, y, z).setColor(r, g, b, a)
        }

        vertex(minX, minY, minZ)
        vertex(minX, minY, maxZ)
        vertex(minX, maxY, maxZ)
        vertex(minX, maxY, minZ)

        vertex(maxX, minY, maxZ)
        vertex(maxX, minY, minZ)
        vertex(maxX, maxY, minZ)
        vertex(maxX, maxY, maxZ)

        vertex(minX, minY, minZ)
        vertex(minX, maxY, minZ)
        vertex(maxX, maxY, minZ)
        vertex(maxX, minY, minZ)

        vertex(maxX, minY, maxZ)
        vertex(maxX, maxY, maxZ)
        vertex(minX, maxY, maxZ)
        vertex(minX, minY, maxZ)

        vertex(minX, minY, minZ)
        vertex(maxX, minY, minZ)
        vertex(maxX, minY, maxZ)
        vertex(minX, minY, maxZ)

        vertex(minX, maxY, maxZ)
        vertex(maxX, maxY, maxZ)
        vertex(maxX, maxY, minZ)
        vertex(minX, maxY, minZ)
    }

    /** Allocation-free single-color line — the batch (tracer-fan) emission primitive. */
    fun renderLine(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        color: Int,
        thickness: Float
    ) {
        val nx = x1 - x0
        val ny = y1 - y0
        val nz = z1 - z0
        buffer.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(thickness)
        buffer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(thickness)
    }

    fun renderVector(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        start: Vector3f,
        direction: Vec3,
        startColor: Int,
        endColor: Int,
        thickness: Float
    ) {
        val endX = start.x() + direction.x.toFloat()
        val endY = start.y() + direction.y.toFloat()
        val endZ = start.z() + direction.z.toFloat()

        val nx = direction.x.toFloat()
        val ny = direction.y.toFloat()
        val nz = direction.z.toFloat()

        buffer.addVertex(pose, start.x(), start.y(), start.z())
            .setColor(startColor)
            .setNormal(pose, nx, ny, nz)
            .setLineWidth(thickness)

        buffer.addVertex(pose, endX, endY, endZ)
            .setColor(endColor)
            .setNormal(pose, nx, ny, nz)
            .setLineWidth(thickness)
    }
}
