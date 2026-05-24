package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.FloydAddonsClient;
import floydaddons.not.dogshit.client.FloydAddonsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(TitleScreen.class)
public class TitleScreenBackgroundMixin {
    @Unique private static Identifier floydaddons$customBgId;
    @Unique private static boolean floydaddons$triedLoad = false;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderCustomBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ensureLoaded()) {
            int w = context.getScaledWindowWidth();
            int h = context.getScaledWindowHeight();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, floydaddons$customBgId, 0, 0, 0f, 0f, w, h, w, h);
            ci.cancel(); // skip vanilla panorama/gradient
        }
    }

    @Unique
    private static boolean ensureLoaded() {
        if (floydaddons$customBgId != null) return true;
        if (floydaddons$triedLoad) return false;
        floydaddons$triedLoad = true;

        Path path = FloydAddonsConfig.getConfigDir().resolve("mainmenu.png");
        if (!Files.exists(path)) return false;
        try {
            NativeImage image = NativeImage.read(Files.newInputStream(path));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "floydaddons_custom_mainmenu", image);
            Identifier id = Identifier.of(FloydAddonsClient.MOD_ID, "custom_mainmenu");
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            floydaddons$customBgId = id;
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
