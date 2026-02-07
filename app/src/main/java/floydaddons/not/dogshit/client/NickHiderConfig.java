package floydaddons.not.dogshit.client;

/**
 * Lightweight in-memory config for the nick hider feature.
 * Kept simple (no disk persistence) since the requirement is client-only presentation.
 */
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NickHiderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("floydaddons-nickhider.json");

    private static String nickname = "George Floyd";
    private static boolean enabled = false;

    private NickHiderConfig() {}

    public static String getNickname() {
        return nickname;
    }

    public static void setNickname(String nick) {
        if (nick != null && !nick.isEmpty()) {
            nickname = nick;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            Data data = GSON.fromJson(r, Data.class);
            if (data != null) {
                if (data.nickname != null && !data.nickname.isEmpty()) nickname = data.nickname;
                enabled = data.enabled;
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        Data data = new Data();
        data.nickname = nickname;
        data.enabled = enabled;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, w);
            }
        } catch (IOException ignored) {
        }
    }

    private static class Data {
        String nickname;
        boolean enabled;
    }
}
