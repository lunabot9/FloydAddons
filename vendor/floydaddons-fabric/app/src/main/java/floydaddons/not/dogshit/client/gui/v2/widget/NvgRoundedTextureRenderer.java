package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/**
 * Uses NanoVG for anti-aliased rounded masks, while Minecraft still draws the
 * actual GUI texture. This avoids NanoVG/Minecraft texture-handle mismatches.
 */
public final class NvgRoundedTextureRenderer {
    private static long vg;
    private static boolean unavailable;
    private static boolean clipping;

    private NvgRoundedTextureRenderer() {
    }

    public static boolean beginRoundedClip(float x, float y, float w, float h, float radius) {
        if (unavailable || clipping || w <= 0f || h <= 0f) return false;

        try {
            if (GL11.glGetInteger(GL11.GL_STENCIL_BITS) <= 0) return false;

            long ctx = context();
            if (ctx == 0L) return false;

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glStencilMask(0xFF);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
            GL11.glColorMask(false, false, false, false);

            MinecraftClient client = MinecraftClient.getInstance();
            Window window = client.getWindow();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                NVGColor color = NVGColor.malloc(stack);
                NanoVG.nvgBeginFrame(ctx, window.getScaledWidth(), window.getScaledHeight(), window.getScaleFactor());
                NanoVG.nvgBeginPath(ctx);
                NanoVG.nvgRoundedRect(ctx, x, y, w, h, radius);
                NanoVG.nvgFillColor(ctx, NanoVG.nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255, color));
                NanoVG.nvgFill(ctx);
                NanoVG.nvgEndFrame(ctx);
            }

            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilMask(0x00);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            clipping = true;
            return true;
        } catch (Throwable ignored) {
            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            unavailable = true;
            clipping = false;
            return false;
        }
    }

    public static void endRoundedClip() {
        if (!clipping) return;
        GL11.glStencilMask(0xFF);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        clipping = false;
    }

    private static long context() {
        if (vg != 0L) return vg;
        vg = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
        if (vg == 0L) {
            unavailable = true;
        }
        return vg;
    }
}
