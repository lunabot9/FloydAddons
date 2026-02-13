package floydaddons.not.dogshit.client.features.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateChecker {
    private static String currentSemver;
    private static String tagSuffix;
    private static final AtomicBoolean checked = new AtomicBoolean(false);
    private static volatile Text pendingMessage;

    private UpdateChecker() {}

    public static void init() {
        try (InputStream is = UpdateChecker.class.getResourceAsStream("/fabric.mod.json")) {
            if (is == null) return;
            JsonObject mod = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            String version = mod.get("version").getAsString();
            if (version.equals("${version}")) return; // dev build

            if (version.contains("-mc")) {
                int idx = version.indexOf("-mc");
                currentSemver = version.substring(0, idx);
                String mcVersion = version.substring(idx + 3); // strip "-mc"
                tagSuffix = "-mc" + mcVersion;
            } else {
                currentSemver = version;
                String mcVersion = SharedConstants.getGameVersion().id();
                tagSuffix = "-mc" + mcVersion;
            }
        } catch (Exception ignored) {}
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) return;

        Text msg = pendingMessage;
        if (msg != null) {
            pendingMessage = null;
            client.player.sendMessage(msg, false);
        }

        if (checked.compareAndSet(false, true)) {
            CompletableFuture.runAsync(UpdateChecker::checkAsync);
        }
    }

    private static void checkAsync() {
        if (currentSemver == null || tagSuffix == null) return;
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/lunabot9/FloydAddons/releases"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
            for (JsonElement elem : releases) {
                JsonObject release = elem.getAsJsonObject();
                if (release.get("draft").getAsBoolean()) continue;
                if (release.get("prerelease").getAsBoolean()) continue;

                String tag = release.get("tag_name").getAsString();
                if (!tag.endsWith(tagSuffix)) continue;

                String remoteSemver = tag;
                if (remoteSemver.startsWith("v")) remoteSemver = remoteSemver.substring(1);
                remoteSemver = remoteSemver.substring(0, remoteSemver.length() - tagSuffix.length());

                if (compareSemver(remoteSemver, currentSemver) > 0) {
                    String url = release.get("html_url").getAsString();
                    pendingMessage = buildMessage(currentSemver, remoteSemver, url);
                }
                break; // only check the first matching release
            }
        } catch (Exception ignored) {}
    }

    private static Text buildMessage(String oldVer, String newVer, String url) {
        MutableText prefix = Text.literal("[FloydAddons] ").formatted(Formatting.GOLD);
        MutableText body = Text.literal("Update available: ")
                .formatted(Formatting.YELLOW)
                .append(Text.literal("v" + oldVer).formatted(Formatting.YELLOW))
                .append(Text.literal(" → ").formatted(Formatting.YELLOW))
                .append(Text.literal("v" + newVer).formatted(Formatting.GREEN))
                .append(Text.literal(". ").formatted(Formatting.YELLOW));
        MutableText link = Text.literal("[Download]").setStyle(Style.EMPTY
                .withColor(Formatting.AQUA)
                .withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open release page"))));
        return prefix.append(body).append(link);
    }

    private static int compareSemver(String a, String b) {
        int[] av = parseSemver(a);
        int[] bv = parseSemver(b);
        for (int i = 0; i < 3; i++) {
            if (av[i] != bv[i]) return Integer.compare(av[i], bv[i]);
        }
        return 0;
    }

    private static int[] parseSemver(String s) {
        String[] parts = s.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
