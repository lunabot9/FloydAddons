package floydaddons.not.dogshit.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class FloydAddonsClient implements ClientModInitializer {
    public static final String MOD_ID = "floydaddons";

    private KeyBinding openGuiKey;
    private static final KeyBinding.Category KEY_CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "category"));

    @Override
    public void onInitializeClient() {
        NickHiderConfig.load();
        SkinConfig.load();
        RenderConfig.load();
        InventoryHudRenderer.register();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.floydaddons.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            while (openGuiKey.wasPressed()) {
                client.setScreen(new FloydAddonsScreen(Text.literal("FloydAddons")));
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            NickHiderConfig.save();
            SkinConfig.save();
            RenderConfig.save();
        });
    }
}


