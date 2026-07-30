package gg.floyd.features.impl.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RotateOnlySwingTransformTest {
    @Test
    fun `right hand swing moves slightly inward and preserves vertical depth`() {
        val pose = PoseStack.Pose()
        pose.translate(1.25f, -0.75f, 0.5f)

        RotateOnlySwingTransform.applySwingTransform(pose, 0.25f, 1)

        assertEquals(1.20f, pose.pose().m30(), 0.0001f)
        assertEquals(-0.75f, pose.pose().m31(), 0.0001f)
        assertEquals(0.5f, pose.pose().m32(), 0.0001f)
    }

    @Test
    fun `left hand swing mirrors the inward movement`() {
        val pose = PoseStack.Pose()
        pose.translate(-1.25f, -0.75f, 0.5f)

        RotateOnlySwingTransform.applySwingTransform(pose, 0.25f, -1)

        assertEquals(-1.20f, pose.pose().m30(), 0.0001f)
        assertEquals(-0.75f, pose.pose().m31(), 0.0001f)
        assertEquals(0.5f, pose.pose().m32(), 0.0001f)
    }

    @Test
    fun `swing rotation is composed before the item display transform`() {
        val pose = PoseStack.Pose()
        val itemTransformPose = PoseStack.Pose()
        itemTransformPose.rotate(Axis.YP.rotationDegrees(90.0f))
        val itemTransform = Matrix4f(itemTransformPose.pose())

        RotateOnlySwingTransform.applySwingTransform(pose, 0.5f, 1)
        val expectedHandSpace = Matrix4f(pose.pose()).mul(itemTransform)
        val oldItemLocalResult = Matrix4f(itemTransform).mul(pose.pose())
        pose.pose().mul(itemTransform)

        assertEquals(expectedHandSpace.m00(), pose.pose().m00(), 0.0001f)
        assertEquals(expectedHandSpace.m01(), pose.pose().m01(), 0.0001f)
        assertEquals(expectedHandSpace.m02(), pose.pose().m02(), 0.0001f)
        assertNotEquals(oldItemLocalResult.m01(), pose.pose().m01(), 0.01f)
    }

    @Test
    fun `downward swing has no sideways rotation components`() {
        val pose = PoseStack.Pose()

        RotateOnlySwingTransform.applySwingTransform(pose, 0.5f, 1)

        assertEquals(0.0f, pose.pose().m01(), 0.0001f)
        assertEquals(0.0f, pose.pose().m02(), 0.0001f)
        assertEquals(0.0f, pose.pose().m10(), 0.0001f)
        assertEquals(0.0f, pose.pose().m20(), 0.0001f)
        assertNotEquals(0.0f, pose.pose().m12(), 0.01f)
    }
}
