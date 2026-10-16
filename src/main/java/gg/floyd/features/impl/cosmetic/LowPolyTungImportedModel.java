package gg.floyd.features.impl.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;

/**
 * OBJ-backed low-poly Tung player model adapted from the public scoliossis Model Modifier example
 * pack. Only the asset parser and render math were ported; no executable gameplay/account code was
 * imported from that repository.
 *
 * Parsing and rendering live in {@link ImportedObjModel}, which also loads user OBJ files from
 * {@code config/floydaddons/models}.
 */
public final class LowPolyTungImportedModel {
    private static final String MODEL_RESOURCE = "assets/floydaddons/player_models/low_poly_tung.obj";
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        "floydaddons", "textures/entity/player_model/low_poly_tung.png"
    );
    private static final Vec3 HELD_ITEM_OFFSET = new Vec3(0.08, -0.15, -0.11);
    /** The bundled model is fitted to the authored player proportions, not to a uniform height. */
    private static final Vec3 TARGET_SIZE = new Vec3(1.5, 2.0, 1.3);
    private static final ImportedObjModel MODEL = load();

    private LowPolyTungImportedModel() {}

    public static Vec3 heldItemOffset(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT
            ? new Vec3(-HELD_ITEM_OFFSET.x, HELD_ITEM_OFFSET.y, HELD_ITEM_OFFSET.z)
            : HELD_ITEM_OFFSET;
    }

    static void render(PoseStack poseStack, SubmitNodeCollector collector, int light, PlayerModel model) {
        MODEL.render(poseStack, collector, light, model, TEXTURE);
    }

    public static void renderFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                            ModelPart arm, HumanoidArm armSide) {
        prepareFirstPersonArm(arm, armSide);
        MODEL.renderFirstPersonArm(poseStack, collector, light, arm, armSide, TEXTURE);
    }

    /**
     * AvatarRenderer normally resets the mutable shared arm before drawing first person. Floyd's
     * custom hand cancels that vanilla method, so it must preserve the same reset here or inventory
     * preview poses and other third-person animation state leak into the next hand frame.
     */
    public static void prepareFirstPersonArm(ModelPart arm, HumanoidArm armSide) {
        arm.resetPose();
        arm.zRot = armSide == HumanoidArm.RIGHT ? 0.1F : -0.1F;
    }

    private static ImportedObjModel load() {
        try (InputStream stream = LowPolyTungImportedModel.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing resource " + MODEL_RESOURCE);
            return ImportedObjModel.parse(stream).fittedToBox(TARGET_SIZE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Low Poly Tung model", exception);
        }
    }
}
