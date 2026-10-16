package gg.floyd.features.impl.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A 3D model loaded from disk for the local player. OBJ files use authored player part groups when
 * present and are otherwise auto-fitted as a single rigid body; GLB files reuse the bundled Tung
 * renderer with an auto-fitted scale.
 */
public final class ImportedFileModel {
    /** Uniform height, in blocks, applied to user models so any export unit scale works. */
    public static final float CUSTOM_MODEL_HEIGHT = 1.8F;

    private final ImportedObjModel obj;
    private final TungImportedModel glb;
    private final Identifier texture;

    private ImportedFileModel(ImportedObjModel obj, TungImportedModel glb, Identifier texture) {
        this.obj = obj;
        this.glb = glb;
        this.texture = texture;
    }

    public static ImportedFileModel load(Path path, Identifier texture, ModelFileFormat format) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            return switch (format) {
                case OBJ -> new ImportedFileModel(
                    ImportedObjModel.parse(stream).fittedToHeight(CUSTOM_MODEL_HEIGHT),
                    null, texture);
                case GLB -> new ImportedFileModel(null, TungImportedModel.parse(stream, texture), texture);
                default -> throw new IOException("Unsupported model format for " + path.getFileName());
            };
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Failed to read " + path.getFileName(), exception);
        }
    }

    public void render(PoseStack stack, SubmitNodeCollector collector, int light, PlayerModel model,
                       float movementSpeed, float attackTime) {
        if (obj != null) {
            obj.render(stack, collector, light, model, texture);
            return;
        }
        if (glb != null) {
            glb.renderModel(stack, collector, light, movementSpeed, attackTime);
        }
    }
}
