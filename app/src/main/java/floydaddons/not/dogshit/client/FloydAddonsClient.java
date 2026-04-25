package floydaddons.not.dogshit.client;
import floydaddons.not.dogshit.client.*;
import floydaddons.not.dogshit.client.config.*;
import floydaddons.not.dogshit.client.gui.*;
import floydaddons.not.dogshit.client.features.hud.*;
import floydaddons.not.dogshit.client.features.visual.*;
import floydaddons.not.dogshit.client.features.cosmetic.*;
import floydaddons.not.dogshit.client.features.misc.*;
import floydaddons.not.dogshit.client.esp.*;
import floydaddons.not.dogshit.client.skin.*;
import floydaddons.not.dogshit.client.util.*;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class FloydAddonsClient implements ClientModInitializer {
    public static final String MOD_ID = "floydaddons";

    private KeyBinding openGuiKey;
    private KeyBinding openV2GuiKey;
    private KeyBinding clickGuiKey;
    private KeyBinding xrayToggleKey;
    private KeyBinding mobEspToggleKey;
    private KeyBinding freecamToggleKey;
    private KeyBinding freelookToggleKey;
    private static final KeyBinding.Category KEY_CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "category"));
    private static boolean windowIconApplied = false;
    private static boolean prevFullscreen = false;

    @Override
    public void onInitializeClient() {
        FloydAddonsConfig.load();
        InventoryHudRenderer.register();
        ScoreboardHudRenderer.register();
        StalkRenderer.register();
        MobEspRenderer.register();
        FloydAddonsCommand.register();
        DiscordPresenceManager.start();
        UpdateChecker.init();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEY_CATEGORY
        ));

        openV2GuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.open_v2_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.click_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KEY_CATEGORY
        ));

        xrayToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.toggle_xray",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        mobEspToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.toggle_mob_esp",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        freecamToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.toggle_freecam",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        freelookToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.toggle_freelook",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client != null && client.getWindow() != null) { // window exists even on title screen
                if (!windowIconApplied) {
                    try { TaskbarIconManager.apply(); } catch (Exception ignored) {}
                    windowIconApplied = true;
                }
                try { RenderConfig.applyWindowTitle(); } catch (Exception ignored) {}
                try { RenderConfig.tickWindowState(); RenderConfig.ensureBorderlessState(); } catch (Exception ignored) {}

                // Preserve user-selected borderless state even after exiting vanilla fullscreen.
                boolean fullscreen = client.getWindow().isFullscreen();
                if (prevFullscreen && !fullscreen && RenderConfig.isBorderlessWindowed()) {
                    try { RenderConfig.applyBorderlessWindowed(true); } catch (Exception ignored) {}
                }
                prevFullscreen = fullscreen;
            }
            SkinManager.extractDefaultSkin(client);
            ServerIdTracker.tick(client);
            NpcTracker.tick();
            MobEspManager.tickScan();
            CapeManager.tickAnimations();
            DiscordPresenceManager.tick(client);
            if (client.player == null) {
                return;
            }
            UpdateChecker.tick(client);
            while (openGuiKey.wasPressed()) {
                client.setScreen(new FloydAddonsScreen(Text.literal("FloydAddons")));
            }
            while (openV2GuiKey.wasPressed()) {
                client.setScreen(new floydaddons.not.dogshit.client.gui.v2.FloydAddonsV2Screen());
            }
            while (clickGuiKey.wasPressed()) {
                client.setScreen(new ClickGuiScreen());
            }
            while (xrayToggleKey.wasPressed()) {
                RenderConfig.toggleXray();
            }
            while (mobEspToggleKey.wasPressed()) {
                RenderConfig.toggleMobEsp();
            }
            while (freecamToggleKey.wasPressed()) {
                CameraConfig.toggleFreecam();
            }
            while (freelookToggleKey.wasPressed()) {
                CameraConfig.toggleFreelook();
            }

            // Client-side time override
            if (RenderConfig.isCustomTimeEnabled() && client.world != null) {
                RenderConfig.applyCustomTime(client.world.getLevelProperties());
            }
        });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityRenderer instanceof PlayerEntityRenderer<?>) {
                        @SuppressWarnings("unchecked")
                        var ctx = (net.minecraft.client.render.entity.feature.FeatureRendererContext<
                                net.minecraft.client.render.entity.state.PlayerEntityRenderState,
                                net.minecraft.client.render.entity.model.PlayerEntityModel>) entityRenderer;
                        registrationHelper.register(new ConeFeatureRenderer(ctx));
                        registrationHelper.register(new CapeFeatureRenderer(ctx));
                    }
                }
        );

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            DiscordPresenceManager.shutdown();
            FloydAddonsConfig.save();
        });
    }
}


