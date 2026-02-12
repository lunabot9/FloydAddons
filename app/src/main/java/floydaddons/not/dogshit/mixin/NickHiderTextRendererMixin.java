package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.util.NickTextUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hooks into TextRenderer at the lowest level to replace player names everywhere.
 *
 * In 1.21.10, all three draw() overloads funnel through prepare():
 *   draw(String, ...)       → prepare(String, ...)
 *   draw(Text, ...)         → text.asOrderedText() → prepare(OrderedText, ...)
 *   draw(OrderedText, ...)  → prepare(OrderedText, ...)
 *
 * By hooking both prepare() overloads, we catch ALL rendered text:
 * tab list, nametags, GUIs, item lore, tooltips, scoreboard, chat input,
 * action bar, boss bar, titles, signs, books, slayer entities, etc.
 *
 * drawWithOutline() has its own rendering path and needs a separate hook.
 */
@Mixin(TextRenderer.class)
public class NickHiderTextRendererMixin {

    // ── prepare() hooks: the true rendering funnel ──

    @ModifyVariable(
        method = "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String floydaddons$replacePrepareString(String text) {
        return NickTextUtil.replaceAllNamesInString(text);
    }

    @ModifyVariable(
        method = "prepare(Lnet/minecraft/text/OrderedText;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private OrderedText floydaddons$replacePrepareOrderedText(OrderedText text) {
        return NickTextUtil.replaceAllNamesInOrderedText(text);
    }

    // ── drawWithOutline: own rendering path, doesn't use prepare() ──

    @ModifyVariable(
        method = "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private OrderedText floydaddons$replaceOutlineText(OrderedText text) {
        return NickTextUtil.replaceAllNamesInOrderedText(text);
    }

    // ── getWidth() hooks: ensures text width calculations match replaced content ──

    @ModifyVariable(
        method = "getWidth(Ljava/lang/String;)I",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String floydaddons$replaceWidthString(String text) {
        return NickTextUtil.replaceAllNamesInString(text);
    }

    @ModifyVariable(
        method = "getWidth(Lnet/minecraft/text/OrderedText;)I",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private OrderedText floydaddons$replaceWidthOrderedText(OrderedText text) {
        return NickTextUtil.replaceAllNamesInOrderedText(text);
    }

    @ModifyVariable(
        method = "getWidth(Lnet/minecraft/text/StringVisitable;)I",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private StringVisitable floydaddons$replaceWidthStringVisitable(StringVisitable text) {
        return NickTextUtil.replaceAllNamesInStringVisitable(text);
    }
}
