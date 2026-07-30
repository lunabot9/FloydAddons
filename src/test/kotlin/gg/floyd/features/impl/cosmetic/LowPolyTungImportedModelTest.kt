package gg.floyd.features.impl.cosmetic

import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.world.entity.HumanoidArm
import kotlin.test.Test
import kotlin.test.assertEquals

class LowPolyTungImportedModelTest {
    @Test
    fun `first person arm discards inventory preview pose before rendering`() {
        val arm = ModelPart(emptyList(), emptyMap())
        arm.setInitialPose(PartPose.offset(5.0f, 2.0f, -1.0f))
        arm.setPos(42.0f, 43.0f, 44.0f)
        arm.setRotation(1.2f, -0.8f, 2.4f)

        LowPolyTungImportedModel.prepareFirstPersonArm(arm, HumanoidArm.RIGHT)

        assertEquals(5.0f, arm.x)
        assertEquals(2.0f, arm.y)
        assertEquals(-1.0f, arm.z)
        assertEquals(0.0f, arm.xRot)
        assertEquals(0.0f, arm.yRot)
        assertEquals(0.1f, arm.zRot)
    }

    @Test
    fun `left first person arm uses the vanilla mirrored wrist angle`() {
        val arm = ModelPart(emptyList(), emptyMap())
        arm.setInitialPose(PartPose.ZERO)
        arm.setRotation(-1.0f, 0.5f, 1.5f)

        LowPolyTungImportedModel.prepareFirstPersonArm(arm, HumanoidArm.LEFT)

        assertEquals(0.0f, arm.xRot)
        assertEquals(0.0f, arm.yRot)
        assertEquals(-0.1f, arm.zRot)
    }
}
