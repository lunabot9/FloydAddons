package net.minecraft.client.renderer

import com.mojang.blaze3d.buffers.GpuBufferSlice

class CachedOrthoProjectionMatrixBuffer(
    name: String,
    private val zNear: Float,
    private val zFar: Float,
    private val invertY: Boolean,
) : AutoCloseable {
    private val delegate = ProjectionMatrixBuffer(name)
    private val projection = Projection()

    fun getBuffer(width: Float, height: Float): GpuBufferSlice {
        projection.setupOrtho(width, height, zNear, zFar, invertY)
        return delegate.getBuffer(projection)
    }

    override fun close() {
        delegate.close()
    }
}
