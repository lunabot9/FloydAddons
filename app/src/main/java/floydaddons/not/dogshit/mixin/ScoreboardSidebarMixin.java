package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.features.hud.ScoreboardHudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the actual scoreboard draw method (the second overload that takes
 * ScoreboardObjective). The first overload resolves the objective and calls this one —
 * if another mod cancels the first overload, this method never runs, and our
 * custom renderer's flag stays unset so it won't render either.
 */
@Mixin(InGameHud.class)
public class ScoreboardSidebarMixin {
    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void floydaddons$interceptScoreboardDraw(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (RenderConfig.isCustomScoreboardEnabled()) {
            ScoreboardHudRenderer.markVanillaWouldRender();
            ci.cancel();
        }
    }
}
