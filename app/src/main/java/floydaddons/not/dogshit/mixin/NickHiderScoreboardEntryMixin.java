package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.NickHiderConfig;
import floydaddons.not.dogshit.client.NickTextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScoreboardEntry.class)
public class NickHiderScoreboardEntryMixin {

    @Inject(method = "display", at = @At("RETURN"), cancellable = true)
    private void floydaddons$nickSidebar(CallbackInfoReturnable<Text> cir) {
        if (!NickHiderConfig.isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null) return;
        String username = client.getSession().getUsername();
        String nick = NickHiderConfig.getNickname();
        Text original = cir.getReturnValue();
        if (original == null) return;
        Text replaced = NickTextUtil.replaceLiteralTextIgnoreCase(original, username, nick);
        cir.setReturnValue(replaced);
    }
}
