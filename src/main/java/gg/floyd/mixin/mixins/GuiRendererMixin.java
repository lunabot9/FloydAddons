package gg.floyd.mixin.mixins;

import gg.floyd.utils.render.ItemStateRenderer;
import gg.floyd.utils.render.PanelBlurPIPRenderer;
import gg.floyd.utils.render.RoundRectPIPRenderer;
import gg.floyd.utils.ui.rendering.NVGPIPRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow
    @Final
    @Mutable
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    /**
     * Minecraft 26.1.2 builds GuiRenderer with a hard-coded List.of(...) containing only Mojang's PIP
     * renderers. Floyd submits custom PictureInPictureRenderState subclasses (ClickGUI NVG, rounded
     * rects, blur panels, GUI items), so extend the live renderer's dispatch map at construction time.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void floydaddons$registerCustomPipRenderers(
        net.minecraft.client.renderer.state.gui.GuiRenderState renderState,
        MultiBufferSource.BufferSource bufferSource,
        net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector,
        net.minecraft.client.renderer.feature.FeatureRenderDispatcher featureRenderDispatcher,
        List<PictureInPictureRenderer<?>> renderers,
        CallbackInfo ci
    ) {
        var extended = new LinkedHashMap<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>>(this.pictureInPictureRenderers);
        addRenderer(extended, new NVGPIPRenderer(bufferSource));
        addRenderer(extended, new RoundRectPIPRenderer(bufferSource));
        addRenderer(extended, new PanelBlurPIPRenderer(bufferSource));
        addRenderer(extended, new ItemStateRenderer(bufferSource));
        this.pictureInPictureRenderers = Map.copyOf(extended);
    }

    private static void addRenderer(
        Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> renderers,
        PictureInPictureRenderer<?> renderer
    ) {
        renderers.put(renderer.getRenderStateClass(), renderer);
    }
}
