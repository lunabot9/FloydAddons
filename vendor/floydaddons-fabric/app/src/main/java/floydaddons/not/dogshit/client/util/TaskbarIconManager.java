package floydaddons.not.dogshit.client.util;

import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Window;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Replaces the game window/taskbar icon with the Floyd Addons image.
 * Runs once after the client has started.
 */
public final class TaskbarIconManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("FloydAddons/TaskbarIcon");
    private static final Identifier[] ICON_IDS = new Identifier[] {
            Identifier.of("floydaddons", "icons/taskbar_icon_16x16.png"),
            Identifier.of("floydaddons", "icons/taskbar_icon_32x32.png"),
            Identifier.of("floydaddons", "icons/taskbar_icon_48x48.png"),
            Identifier.of("floydaddons", "icons/taskbar_icon_128x128.png")
    };

    private TaskbarIconManager() {}

    public static void apply() {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        ResourceManager resources = client.getResourceManager();

        List<LoadedIcon> loaded = new ArrayList<>();
        for (Identifier id : ICON_IDS) {
            loadIcon(resources, id).ifPresent(loaded::add);
        }

        if (loaded.isEmpty()) {
            LOGGER.warn("Taskbar icon images missing; leaving default window icon.");
            return;
        }

        MinecraftClient.getInstance().execute(() -> {
            GLFWImage.Buffer glfwImages = GLFWImage.malloc(loaded.size());
            for (int i = 0; i < loaded.size(); i++) {
                LoadedIcon icon = loaded.get(i);
                glfwImages.position(i);
                glfwImages.width(icon.width());
                glfwImages.height(icon.height());
                glfwImages.pixels(icon.pixels());
            }
            glfwImages.position(0);

            try {
                GLFW.glfwSetWindowIcon(window.getHandle(), glfwImages);
            } catch (Exception e) {
                LOGGER.warn("Failed to set GLFW window icon", e);
            } finally {
                glfwImages.free();
                loaded.forEach(icon -> MemoryUtil.memFree(icon.pixels()));
            }
        });
    }

    private static Optional<LoadedIcon> loadIcon(ResourceManager resources, Identifier id) {
        try {
            Optional<Resource> resourceOpt = resources.getResource(id);
            if (resourceOpt.isEmpty()) {
                LOGGER.warn("Missing icon resource {}", id);
                return Optional.empty();
            }
            Resource resource = resourceOpt.get();
            try (InputStream stream = resource.getInputStream(); NativeImage image = NativeImage.read(stream)) {
                int[] pixels = image.copyPixelsAbgr(); // ABGR ints, little-endian RGBA in byte order
                ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length * 4);
                for (int pixel : pixels) {
                    buffer.put((byte) (pixel));
                    buffer.put((byte) (pixel >> 8));
                    buffer.put((byte) (pixel >> 16));
                    buffer.put((byte) (pixel >> 24));
                }
                buffer.flip();
                return Optional.of(new LoadedIcon(image.getWidth(), image.getHeight(), buffer));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load icon {}", id, e);
            return Optional.empty();
        }
    }

    private record LoadedIcon(int width, int height, ByteBuffer pixels) {}
}
