package gg.floyd.features.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public final class RotateOnlySwingTransform {
    private static final float DOWNWARD_ROTATION_DEGREES = 95.0f;
    private static final float INWARD_TRAVEL = 0.05f;

    private record ActiveSwing(float progress, int direction) {}

    private static final ThreadLocal<ActiveSwing> ACTIVE_SWING = new ThreadLocal<>();

    private RotateOnlySwingTransform() {}

    public static void begin(float swingProgress, HumanoidArm arm) {
        if (!FloydAnimations.shouldUseRotateOnlySwing()) {
            ACTIVE_SWING.remove();
            return;
        }
        ACTIVE_SWING.set(new ActiveSwing(swingProgress, arm == HumanoidArm.RIGHT ? 1 : -1));
    }

    public static void end() {
        ACTIVE_SWING.remove();
    }

    public static void applyIfActive(PoseStack.Pose pose) {
        ActiveSwing swing = ACTIVE_SWING.get();
        if (swing == null) return;

        applySwingTransform(pose, swing.progress(), swing.direction());
        FloydAnimations.recordRotateOnlySwing();
    }

    public static void applySwingTransform(PoseStack.Pose pose, float swingProgress, int direction) {
        float rootSwing = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);

        pose.translate(direction * -INWARD_TRAVEL * rootSwing, 0.0f, 0.0f);

        // Apply the dominant downward component from Dulkir's swing transform.
        // Omitting its small Y/Z components prevents the rightward hook the
        // user sees on tools while retaining the downward rotation.
        pose.rotate(Axis.XP.rotationDegrees(-DOWNWARD_ROTATION_DEGREES * rootSwing));
    }
}
