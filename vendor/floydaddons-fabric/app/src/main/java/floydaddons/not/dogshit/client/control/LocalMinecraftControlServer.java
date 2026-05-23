package floydaddons.not.dogshit.client.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import floydaddons.not.dogshit.client.FloydAddonsConfig;
import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.features.cosmetic.CapeManager;
import floydaddons.not.dogshit.client.features.cosmetic.ConeHatManager;
import floydaddons.not.dogshit.client.gui.XrayEditorScreen;
import floydaddons.not.dogshit.client.gui.MobEspEditorScreen;
import floydaddons.not.dogshit.client.gui.v2.FloydAddonsV2Screen;
import floydaddons.not.dogshit.client.skin.SkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Temporary local bridge for development agents. It is intentionally loopback-only
 * and token-protected because it can control the active Minecraft client.
 */
public final class LocalMinecraftControlServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("FloydAddons/Control");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SETTINGS_PATH = FloydAddonsConfig.getConfigDir().resolve("control-bridge.json");
    private static final int DEFAULT_PORT = 38765;
    private static final int MAX_BODY_BYTES = 8192;

    private static HttpServer server;
    private static ScheduledExecutorService scheduler;
    private static Settings settings;

    private LocalMinecraftControlServer() {}

    public static synchronized void start() {
        if (server != null) return;

        try {
            settings = loadSettings();
            if (!settings.enabled) {
                LOGGER.info("Local Minecraft control bridge disabled in {}", SETTINGS_PATH);
                return;
            }

            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), settings.port), 0);
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "FloydAddons-Control-Scheduler");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread thread = new Thread(r, "FloydAddons-Control-HTTP");
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/", LocalMinecraftControlServer::handle);
            server.start();
            LOGGER.info("Local Minecraft control bridge listening on http://127.0.0.1:{}", settings.port);
            LOGGER.info("Control bridge token/config: {}", SETTINGS_PATH);
        } catch (IOException e) {
            LOGGER.warn("Failed to start local Minecraft control bridge", e);
            server = null;
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static boolean isRunning() {
        return server != null;
    }

    public static int getPort() {
        return settings == null ? DEFAULT_PORT : settings.port;
    }

    public static Path getSettingsPath() {
        return SETTINGS_PATH;
    }

    private static Settings loadSettings() throws IOException {
        Files.createDirectories(FloydAddonsConfig.getConfigDir());
        if (Files.exists(SETTINGS_PATH)) {
            try {
                Settings loaded = GSON.fromJson(Files.readString(SETTINGS_PATH), Settings.class);
                if (loaded != null && loaded.token != null && !loaded.token.isBlank()) {
                    loaded.port = loaded.port <= 0 ? DEFAULT_PORT : loaded.port;
                    return loaded;
                }
            } catch (JsonSyntaxException ignored) {
            }
        }

        Settings created = new Settings();
        created.enabled = true;
        created.port = DEFAULT_PORT;
        created.token = generateToken();
        Files.writeString(SETTINGS_PATH, GSON.toJson(created), StandardCharsets.UTF_8);
        return created;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isLoopback(exchange)) {
                send(exchange, 403, Map.of("ok", false, "error", "loopback_only"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if ("/health".equals(path) || "/".equals(path)) {
                send(exchange, 200, Map.of(
                        "ok", true,
                        "service", "floydaddons-control",
                        "port", getPort(),
                        "auth", "Authorization: Bearer <token>",
                        "settings", SETTINGS_PATH.toString(),
                        "endpoints", List.of("/state", "/chat", "/look", "/hotbar", "/key", "/action",
                                "/screen", "/mouse", "/screenshot")
                ));
                return;
            }

            if (!isAuthorized(exchange)) {
                send(exchange, 401, Map.of("ok", false, "error", "unauthorized"));
                return;
            }

            switch (path) {
                case "/state" -> requireMethod(exchange, "GET", () -> send(exchange, 200, getState()));
                case "/chat" -> requireMethod(exchange, "POST", () -> handleChat(exchange));
                case "/look" -> requireMethod(exchange, "POST", () -> handleLook(exchange));
                case "/hotbar" -> requireMethod(exchange, "POST", () -> handleHotbar(exchange));
                case "/key" -> requireMethod(exchange, "POST", () -> handleKey(exchange));
                case "/action" -> requireMethod(exchange, "POST", () -> handleAction(exchange));
                case "/screen" -> requireMethod(exchange, "POST", () -> handleScreen(exchange));
                case "/mouse" -> requireMethod(exchange, "POST", () -> handleMouse(exchange));
                case "/screenshot" -> requireMethod(exchange, "POST", () -> handleScreenshot(exchange));
                default -> send(exchange, 404, Map.of("ok", false, "error", "not_found"));
            }
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            LOGGER.warn("Control bridge request failed", e);
            send(exchange, 500, Map.of("ok", false, "error", e.getClass().getSimpleName()));
        } finally {
            exchange.close();
        }
    }

    private static void requireMethod(HttpExchange exchange, String method, RequestHandler handler) throws Exception {
        if (!method.equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("ok", false, "error", "method_not_allowed"));
            return;
        }
        handler.handle();
    }

    private static Map<String, Object> getState() throws Exception {
        return callClient(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("ok", true);
            root.put("connected", client.player != null && client.world != null);
            root.put("screen", client.currentScreen == null ? null : client.currentScreen.getClass().getName());
            root.put("screenTitle", client.currentScreen == null ? null : client.currentScreen.getTitle().getString());
            root.put("window", Map.of(
                    "fullscreen", client.getWindow().isFullscreen(),
                    "borderlessWindowed", RenderConfig.isBorderlessWindowed(),
                    "width", client.getWindow().getWidth(),
                    "height", client.getWindow().getHeight(),
                    "scaledWidth", client.getWindow().getScaledWidth(),
                    "scaledHeight", client.getWindow().getScaledHeight()
            ));

            if (client.player == null || client.world == null) {
                return root;
            }

            var player = client.player;
            root.put("player", Map.of(
                    "name", player.getName().getString(),
                    "x", player.getX(),
                    "y", player.getY(),
                    "z", player.getZ(),
                    "yaw", player.getYaw(),
                    "pitch", player.getPitch(),
                    "health", player.getHealth(),
                    "maxHealth", player.getMaxHealth(),
                    "food", player.getHungerManager().getFoodLevel(),
                    "onGround", player.isOnGround()
            ));
            root.put("world", Map.of(
                    "dimension", client.world.getRegistryKey().getValue().toString(),
                    "time", client.world.getTime(),
                    "timeOfDay", client.world.getTimeOfDay()
            ));
            root.put("crosshair", describeHit(client));
            root.put("hotbar", describeHotbar(player.getInventory()));
            root.put("nearbyEntities", describeNearbyEntities(client));
            return root;
        });
    }

    private static void handleChat(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        String message = requiredString(body, "message");
        callClient(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getNetworkHandler() == null) {
                throw new IllegalArgumentException("not_connected");
            }
            if (message.startsWith("/")) {
                client.getNetworkHandler().sendChatCommand(message.substring(1));
            } else {
                client.getNetworkHandler().sendChatMessage(message);
            }
            return null;
        });
        send(exchange, 200, Map.of("ok", true));
    }

    private static void handleLook(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        callClient(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                throw new IllegalArgumentException("not_connected");
            }
            float yaw = hasNumber(body, "yaw") ? body.get("yaw").getAsFloat() : client.player.getYaw();
            float pitch = hasNumber(body, "pitch") ? body.get("pitch").getAsFloat() : client.player.getPitch();
            yaw += hasNumber(body, "deltaYaw") ? body.get("deltaYaw").getAsFloat() : 0f;
            pitch += hasNumber(body, "deltaPitch") ? body.get("deltaPitch").getAsFloat() : 0f;
            client.player.setYaw(yaw);
            client.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
            client.player.setHeadYaw(client.player.getYaw());
            return null;
        });
        send(exchange, 200, Map.of("ok", true));
    }

    private static void handleHotbar(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        int slot = requiredInt(body, "slot");
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("slot_must_be_0_to_8");
        }
        callClient(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                throw new IllegalArgumentException("not_connected");
            }
            client.player.getInventory().setSelectedSlot(slot);
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            return null;
        });
        send(exchange, 200, Map.of("ok", true, "slot", slot));
    }

    private static void handleKey(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        String keyName = requiredString(body, "key");
        boolean pressed = !body.has("pressed") || body.get("pressed").getAsBoolean();
        long durationMs = hasNumber(body, "durationMs") ? Math.max(0L, body.get("durationMs").getAsLong()) : 0L;

        callClient(() -> {
            KeyBinding key = keyByName(keyName);
            key.setPressed(pressed);
            if (pressed && durationMs > 0 && scheduler != null) {
                scheduler.schedule(() -> MinecraftClient.getInstance().execute(() -> key.setPressed(false)),
                        durationMs, TimeUnit.MILLISECONDS);
            }
            return null;
        });
        send(exchange, 200, Map.of("ok", true, "key", keyName, "pressed", pressed));
    }

    private static void handleAction(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        String action = requiredString(body, "action");
        callClient(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            switch (action) {
                case "attack" -> tapKey(client.options.attackKey, body);
                case "use" -> tapKey(client.options.useKey, body);
                case "closeScreen" -> client.setScreen(null);
                case "jump" -> tapKey(client.options.jumpKey, body);
                case "sneak" -> tapKey(client.options.sneakKey, body);
                case "sprint" -> tapKey(client.options.sprintKey, body);
                case "fullscreen" -> {
                    boolean enabled = !body.has("enabled") || body.get("enabled").getAsBoolean();
                    if (client.getWindow().isFullscreen() != enabled) {
                        client.getWindow().toggleFullscreen();
                    }
                }
                case "windowedFullscreen", "borderlessWindowed" -> {
                    boolean enabled = !body.has("enabled") || body.get("enabled").getAsBoolean();
                    if (enabled && client.getWindow().isFullscreen()) {
                        client.getWindow().toggleFullscreen();
                    }
                    RenderConfig.setBorderlessWindowed(enabled);
                    RenderConfig.applyBorderlessWindowed(true);
                }
                case "reloadMod", "reloadResources" -> {
                    FloydAddonsConfig.load();
                    SkinManager.clearCache();
                    CapeManager.listAvailableImages(true);
                    ConeHatManager.clearCache();
                    RenderConfig.rebuildChunks();
                    client.reloadResources();
                }
                default -> throw new IllegalArgumentException("unknown_action");
            }
            return null;
        });
        send(exchange, 200, Map.of("ok", true, "action", action));
    }

    private static void handleScreen(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        String screen = requiredString(body, "screen");
        callClient(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            switch (screen) {
                case "floyd", "floydaddons", "v2" -> client.setScreen(new FloydAddonsV2Screen());
                case "xrayEditor", "xrayBlocks" -> client.setScreen(new XrayEditorScreen(client.currentScreen));
                case "mobEspEditor", "mobEspFilters" -> client.setScreen(new MobEspEditorScreen(client.currentScreen));
                case "close", "none" -> client.setScreen(null);
                default -> throw new IllegalArgumentException("unknown_screen");
            }
            return null;
        });
        send(exchange, 200, Map.of("ok", true, "screen", screen));
    }

    private static void handleMouse(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        String event = body.has("event") && !body.get("event").isJsonNull()
                ? body.get("event").getAsString() : "click";
        double x = requiredNumber(body, "x");
        double y = requiredNumber(body, "y");
        int button = hasNumber(body, "button") ? body.get("button").getAsInt() : 0;
        double horizontalAmount = hasNumber(body, "horizontalAmount") ? body.get("horizontalAmount").getAsDouble() : 0.0;
        double verticalAmount = hasNumber(body, "verticalAmount") ? body.get("verticalAmount").getAsDouble()
                : hasNumber(body, "amount") ? body.get("amount").getAsDouble() : 0.0;
        callClient(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen == null) {
                throw new IllegalArgumentException("no_screen");
            }
            Click click = new Click(x, y, new MouseInput(button, 0));
            switch (event) {
                case "click" -> {
                    client.currentScreen.mouseClicked(click, false);
                    client.currentScreen.mouseReleased(click);
                }
                case "down", "press" -> client.currentScreen.mouseClicked(click, false);
                case "up", "release" -> client.currentScreen.mouseReleased(click);
                case "scroll", "wheel" -> client.currentScreen.mouseScrolled(x, y, horizontalAmount, verticalAmount);
                default -> throw new IllegalArgumentException("unknown_mouse_event");
            }
            return null;
        });
        send(exchange, 200, Map.of("ok", true, "event", event, "x", x, "y", y, "button", button,
                "horizontalAmount", horizontalAmount, "verticalAmount", verticalAmount));
    }

    private static void handleScreenshot(HttpExchange exchange) throws Exception {
        JsonObject body = readJson(exchange);
        String fileName = body.has("fileName") && !body.get("fileName").isJsonNull()
                ? body.get("fileName").getAsString() : "floyd-control.png";
        if (!fileName.endsWith(".png") || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("invalid_fileName");
        }

        CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                Path screenshotsDir = client.runDirectory.toPath().resolve("screenshots");
                Path output = screenshotsDir.resolve(fileName);
                ScreenshotRecorder.saveScreenshot(client.runDirectory, fileName, client.getFramebuffer(), 1, message -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("ok", true);
                    payload.put("path", output.toString());
                    payload.put("message", message.getString());
                    payload.put("screen", client.currentScreen == null ? null : client.currentScreen.getClass().getName());
                    result.complete(payload);
                });
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });

        try {
            send(exchange, 200, result.get(5, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            throw new IllegalArgumentException("screenshot_timeout");
        }
    }

    private static void tapKey(KeyBinding key, JsonObject body) {
        long durationMs = hasNumber(body, "durationMs") ? Math.max(0L, body.get("durationMs").getAsLong()) : 120L;
        key.setPressed(true);
        if (scheduler != null) {
            scheduler.schedule(() -> MinecraftClient.getInstance().execute(() -> key.setPressed(false)),
                    durationMs, TimeUnit.MILLISECONDS);
        }
    }

    private static KeyBinding keyByName(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        return switch (name) {
            case "forward", "w" -> client.options.forwardKey;
            case "back", "s" -> client.options.backKey;
            case "left", "a" -> client.options.leftKey;
            case "right", "d" -> client.options.rightKey;
            case "jump", "space" -> client.options.jumpKey;
            case "sneak", "shift" -> client.options.sneakKey;
            case "sprint", "ctrl" -> client.options.sprintKey;
            case "attack", "mouse1" -> client.options.attackKey;
            case "use", "mouse2" -> client.options.useKey;
            default -> throw new IllegalArgumentException("unknown_key");
        };
    }

    private static Map<String, Object> describeHit(MinecraftClient client) {
        Map<String, Object> result = new LinkedHashMap<>();
        HitResult hit = client.crosshairTarget;
        if (hit == null) {
            result.put("type", "NONE");
            return result;
        }

        result.put("type", hit.getType().name());
        result.put("x", hit.getPos().x);
        result.put("y", hit.getPos().y);
        result.put("z", hit.getPos().z);
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            result.put("blockPos", Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
            result.put("side", blockHit.getSide().asString());
            if (client.world != null) {
                result.put("block", Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString());
            }
        } else if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            result.put("entity", describeEntity(entity));
        }
        return result;
    }

    private static List<Map<String, Object>> describeHotbar(PlayerInventory inventory) {
        return java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> {
                    Map<String, Object> slot = new LinkedHashMap<>();
                    slot.put("slot", i);
                    slot.put("selected", i == inventory.getSelectedSlot());
                    slot.put("item", describeStack(inventory.getStack(i)));
                    return slot;
                })
                .toList();
    }

    private static Map<String, Object> describeStack(ItemStack stack) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("empty", stack.isEmpty());
        if (!stack.isEmpty()) {
            item.put("id", Registries.ITEM.getId(stack.getItem()).toString());
            item.put("name", stack.getName().getString());
            item.put("count", stack.getCount());
        }
        return item;
    }

    private static List<Map<String, Object>> describeNearbyEntities(MinecraftClient client) {
        if (client.world == null || client.player == null) return List.of();
        java.util.ArrayList<Map<String, Object>> entities = new java.util.ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.squaredDistanceTo(client.player) > 256) continue;
            entities.add(describeEntity(entity));
            if (entities.size() >= 24) break;
        }
        return entities;
    }

    private static Map<String, Object> describeEntity(Entity entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("type", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        result.put("name", entity.getName().getString());
        result.put("x", entity.getX());
        result.put("y", entity.getY());
        result.put("z", entity.getZ());
        return result;
    }

    private static <T> T callClient(Callable<T> callable) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            return callable.call();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw e;
        }
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream input = exchange.getRequestBody()) {
            bytes = input.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (bytes.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("body_too_large");
        }
        if (bytes.length == 0) return new JsonObject();
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String requiredString(JsonObject body, String key) {
        if (!body.has(key) || body.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing_" + key);
        }
        String value = body.get(key).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("blank_" + key);
        }
        return value;
    }

    private static int requiredInt(JsonObject body, String key) {
        if (!hasNumber(body, key)) {
            throw new IllegalArgumentException("missing_" + key);
        }
        return body.get(key).getAsInt();
    }

    private static double requiredNumber(JsonObject body, String key) {
        if (!hasNumber(body, key)) {
            throw new IllegalArgumentException("missing_" + key);
        }
        return body.get(key).getAsDouble();
    }

    private static boolean hasNumber(JsonObject body, String key) {
        return body.has(key) && !body.get(key).isJsonNull() && body.get(key).isJsonPrimitive()
                && body.get(key).getAsJsonPrimitive().isNumber();
    }

    private static boolean isLoopback(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress().getAddress();
        return address.isLoopbackAddress();
    }

    private static boolean isAuthorized(HttpExchange exchange) {
        if (settings == null || settings.token == null) return false;

        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.equals("Bearer " + settings.token)) return true;

        String tokenHeader = exchange.getRequestHeaders().getFirst("X-FloydAddons-Token");
        if (settings.token.equals(tokenHeader)) return true;

        String queryToken = queryParams(exchange).get("token");
        return settings.token.equals(queryToken);
    }

    private static Map<String, String> queryParams(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) return Map.of();
        Map<String, String> params = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] payload = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private interface RequestHandler {
        void handle() throws Exception;
    }

    private static final class Settings {
        boolean enabled = true;
        int port = DEFAULT_PORT;
        String token;
    }
}
