package gg.floyd.utils.render

//? if >=26.2 {
/*import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class RecordedVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    var color: Int? = null,
    var uv: Pair<Float, Float>? = null,
    var uv1: Pair<Int, Int>? = null,
    var uv2: Pair<Int, Int>? = null,
    var normal: Triple<Float, Float, Float>? = null,
    var lineWidth: Float? = null,
)

private class RecordingVertexConsumer : VertexConsumer {
    val vertices = ArrayList<RecordedVertex>()
    private val current get() = vertices.last()

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
        vertices += RecordedVertex(x, y, z)
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer =
        setColor((alpha and 255 shl 24) or (red and 255 shl 16) or (green and 255 shl 8) or (blue and 255))

    override fun setColor(color: Int): VertexConsumer = apply { current.color = color }
    override fun setUv(u: Float, v: Float): VertexConsumer = apply { current.uv = u to v }
    override fun setUv1(u: Int, v: Int): VertexConsumer = apply { current.uv1 = u to v }
    override fun setUv2(u: Int, v: Int): VertexConsumer = apply { current.uv2 = u to v }
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = apply { current.normal = Triple(x, y, z) }
    override fun setLineWidth(width: Float): VertexConsumer = apply { current.lineWidth = width }
}

/** Bridges the removed BufferSource#getBuffer path onto 26.2's backend-neutral submit nodes. */
internal fun SubmitNodeCollector.getBuffer(renderType: RenderType): VertexConsumer {
    val recording = RecordingVertexConsumer()
    submitCustomGeometry(PoseStack(), renderType) { _, target ->
        for (vertex in recording.vertices) {
            var out = target.addVertex(vertex.x, vertex.y, vertex.z)
            vertex.color?.let { out = out.setColor(it) }
            vertex.uv?.let { out = out.setUv(it.first, it.second) }
            vertex.uv1?.let { out = out.setUv1(it.first, it.second) }
            vertex.uv2?.let { out = out.setUv2(it.first, it.second) }
            vertex.normal?.let { out = out.setNormal(it.first, it.second, it.third) }
            vertex.lineWidth?.let { out.setLineWidth(it) }
        }
    }
    return recording
}

internal object SubmitNodeRenderScope {
    var current: SubmitNodeCollector? = null
}

internal fun SubmitNodeCollector.submitRoundedRect(
    pose: PoseStack, width: Float, height: Float, radii: FloatArray, colors: IntArray,
) {
    if (width <= 0f || height <= 0f) return
    val out = getBuffer(RenderTypes.debugTriangleFan())
    fun mix(a: Int, b: Int, t: Float): Int {
        val u = 1f - t
        val aa = (((a ushr 24) and 255) * u + ((b ushr 24) and 255) * t).toInt()
        val rr = (((a ushr 16) and 255) * u + ((b ushr 16) and 255) * t).toInt()
        val gg = (((a ushr 8) and 255) * u + ((b ushr 8) and 255) * t).toInt()
        val bb = ((a and 255) * u + (b and 255) * t).toInt()
        return (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }
    fun colorAt(x: Float, y: Float): Int {
        val tx = (x / width).coerceIn(0f, 1f)
        val ty = (y / height).coerceIn(0f, 1f)
        return mix(mix(colors[0], colors[1], tx), mix(colors[3], colors[2], tx), ty)
    }
    val matrix = pose.last()
    out.addVertex(matrix, width * .5f, height * .5f, 0f).setColor(colorAt(width * .5f, height * .5f))
    val clamped = FloatArray(4) { radii[it].coerceIn(0f, minOf(width, height) * .5f) }
    val corners = arrayOf(
        floatArrayOf(clamped[0], clamped[0], PI.toFloat(), (PI * 1.5).toFloat(), clamped[0]),
        floatArrayOf(width - clamped[1], clamped[1], (PI * 1.5).toFloat(), (PI * 2.0).toFloat(), clamped[1]),
        floatArrayOf(width - clamped[2], height - clamped[2], 0f, (PI * .5).toFloat(), clamped[2]),
        floatArrayOf(clamped[3], height - clamped[3], (PI * .5).toFloat(), PI.toFloat(), clamped[3]),
    )
    var firstX = 0f; var firstY = 0f; var first = true
    for (corner in corners) for (step in 0..8) {
        val angle = corner[2] + (corner[3] - corner[2]) * (step / 8f)
        val x = corner[0] + cos(angle) * corner[4]
        val y = corner[1] + sin(angle) * corner[4]
        if (first) { firstX = x; firstY = y; first = false }
        out.addVertex(matrix, x, y, 0f).setColor(colorAt(x, y))
    }
    out.addVertex(matrix, firstX, firstY, 0f).setColor(colorAt(firstX, firstY))
}*///?}
