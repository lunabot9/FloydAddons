package floydaddons.not.dogshit.client.features.misc;
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

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import net.minecraft.client.MinecraftClient;

import java.time.Instant;

/**
 * Lightweight Discord Rich Presence wrapper.
 * Uses a shared Discord application/asset so all players show presence by default.
 * Env overrides (optional):
 *   FLOYDADDONS_DISCORD_APP_ID         - override the bundled app id
 *   FLOYDADDONS_DISCORD_LARGE_IMAGE    - override the large image asset key
 */
public final class DiscordPresenceManager {
    private static final String DEFAULT_APP_ID = "1471448522279747595";
    private static final String DEFAULT_LARGE_IMAGE_KEY = "floydaddons_icon";
    private static final String APP_ID = System.getenv()
            .getOrDefault("FLOYDADDONS_DISCORD_APP_ID", DEFAULT_APP_ID)
            .trim();
    private static final String LARGE_IMAGE_KEY = System.getenv()
            .getOrDefault("FLOYDADDONS_DISCORD_LARGE_IMAGE", DEFAULT_LARGE_IMAGE_KEY)
            .trim();
    private static final long SESSION_START = Instant.now().getEpochSecond();

    private static Thread callbackThread;
    private static volatile boolean initialized;
    private static volatile boolean failed;
    private static String lastState = "";
    private static volatile DiscordRPC rpc;

    private DiscordPresenceManager() {}

    public static void start() {
        if (initialized || failed || APP_ID.isEmpty()) {
            return;
        }
        try {
            DiscordRPC r = ensureRpc();
            if (r == null) {
                failed = true;
                return;
            }
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            r.Discord_Initialize(APP_ID, handlers, true, null);
            updatePresence("In menus");
            callbackThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        r.Discord_RunCallbacks();
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (UnsatisfiedLinkError | Exception e) {
                        failed = true;
                        Thread.currentThread().interrupt();
                    }
                }
            }, "FloydAddons-DiscordRPC");
            callbackThread.setDaemon(true);
            callbackThread.start();
            initialized = true;
        } catch (UnsatisfiedLinkError | Exception e) {
            failed = true;
        }
    }

    public static void tick(MinecraftClient client) {
        if (!initialized || failed) {
            return;
        }
        String state = computeState(client);
        if (!state.equals(lastState)) {
            lastState = state;
            updatePresence(state);
        }
    }

    private static String computeState(MinecraftClient client) {
        if (client == null) {
            return "Loading Minecraft";
        }
        if (client.getCurrentServerEntry() != null) {
            var entry = client.getCurrentServerEntry();
            var name = (entry.name != null && !entry.name.isBlank()) ? entry.name : entry.address;
            return name != null && !name.isBlank() ? "Multiplayer: " + name : "Multiplayer";
        }
        if (client.isIntegratedServerRunning()) {
            return "Singleplayer world";
        }
        return "In menus";
    }

    private static void updatePresence(String state) {
        DiscordRichPresence presence = new DiscordRichPresence();
        presence.details = "Playing Floyd Addons";
        presence.state = state;
        presence.largeImageKey = LARGE_IMAGE_KEY;
        presence.largeImageText = "Floyd Addons";
        presence.startTimestamp = SESSION_START;
        DiscordRPC r = rpc;
        if (r != null) {
            r.Discord_UpdatePresence(presence);
        }
    }

    public static void shutdown() {
        if (!initialized) {
            return;
        }
        if (callbackThread != null) {
            callbackThread.interrupt();
            callbackThread = null;
        }
        try {
            DiscordRPC r = rpc;
            if (r != null) {
                r.Discord_ClearPresence();
                r.Discord_Shutdown();
            }
        } catch (UnsatisfiedLinkError ignored) {
            // Native already gone; nothing to do.
        }
        initialized = false;
    }

    private static DiscordRPC ensureRpc() {
        if (rpc != null) return rpc;
        try {
            rpc = DiscordRPC.INSTANCE;
        } catch (UnsatisfiedLinkError e) {
            failed = true;
            rpc = null;
        }
        return rpc;
    }
}
