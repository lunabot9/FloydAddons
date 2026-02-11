package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small helper to replace occurrences of a string within literal text nodes,
 * preserving styles and keeping other components intact.
 */
public final class NickTextUtil {
    private NickTextUtil() {}

    public static Text replaceLiteralText(Text original, String find, String replace) {
        return replaceLiteralTextInternal(original, find, replace, false);
    }

    public static Text replaceLiteralTextIgnoreCase(Text original, String find, String replace) {
        return replaceLiteralTextInternal(original, find, replace, true);
    }

    /**
     * Replaces all occurrences of {@code find} in an OrderedText, preserving per-character styles.
     */
    public static OrderedText replaceInOrderedText(OrderedText original, String find, String replace) {
        if (original == null || find == null || replace == null || find.isEmpty()) return original;

        List<Integer> codePoints = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        original.accept((index, style, codePoint) -> {
            codePoints.add(codePoint);
            styles.add(style);
            sb.appendCodePoint(codePoint);
            return true;
        });

        if (codePoints.isEmpty()) return original;

        String text = sb.toString();

        // Supplementary character fallback (rare — emoji etc.)
        if (text.length() != codePoints.size()) {
            String replaced = caseInsensitiveReplace(text, find, replace);
            if (replaced.equals(text)) return original;
            return OrderedText.styledForwardsVisitedString(replaced, styles.get(0));
        }

        String textLower = text.toLowerCase();
        String findLower = find.toLowerCase();

        if (!textLower.contains(findLower)) return original;

        List<Integer> resultCPs = new ArrayList<>();
        List<Style> resultStyles = new ArrayList<>();

        int idx = 0;
        while (idx < codePoints.size()) {
            int hit = textLower.indexOf(findLower, idx);
            if (hit < 0) {
                for (int i = idx; i < codePoints.size(); i++) {
                    resultCPs.add(codePoints.get(i));
                    resultStyles.add(styles.get(i));
                }
                break;
            }
            for (int i = idx; i < hit; i++) {
                resultCPs.add(codePoints.get(i));
                resultStyles.add(styles.get(i));
            }
            Style matchStyle = styles.get(hit);
            for (int i = 0; i < replace.length(); i++) {
                resultCPs.add((int) replace.charAt(i));
                resultStyles.add(matchStyle);
            }
            idx = hit + find.length();
        }

        List<Integer> finalCPs = List.copyOf(resultCPs);
        List<Style> finalStyles = List.copyOf(resultStyles);
        return visitor -> {
            for (int i = 0; i < finalCPs.size(); i++) {
                if (!visitor.accept(i, finalStyles.get(i), finalCPs.get(i))) return false;
            }
            return true;
        };
    }

    /**
     * Applies all nick-hider replacements (self + others) to rendered OrderedText.
     * Also applies server ID hider independently.
     * Called from the TextRenderer mixin on every piece of text drawn to screen.
     */
    public static OrderedText replaceAllNamesInOrderedText(OrderedText original) {
        boolean nickEnabled = NickHiderConfig.isEnabled();
        boolean serverIdEnabled = RenderConfig.isServerIdHiderEnabled();
        if (!nickEnabled && !serverIdEnabled) return original;

        OrderedText result = original;

        if (nickEnabled) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSession() != null) {
                String username = client.getSession().getUsername();
                if (username != null && !username.isEmpty()) {
                    result = replaceInOrderedText(result, username, NickHiderConfig.getNickname());
                }
            }

            for (Map.Entry<String, String> entry : NickHiderConfig.getNameMappings().entrySet()) {
                result = replaceInOrderedText(result, entry.getKey(), entry.getValue());
            }
        }

        if (serverIdEnabled) {
            String replacement = RenderConfig.getServerIdReplacement();
            for (String id : ServerIdTracker.getCachedIds()) {
                result = replaceInOrderedText(result, id, replacement);
            }
        }

        return result;
    }

    /**
     * Applies all nick-hider replacements to a raw String.
     * Also applies server ID hider independently.
     * Called from the TextRenderer mixin for String-based draw/prepare/getWidth calls.
     */
    public static String replaceAllNamesInString(String text) {
        boolean nickEnabled = NickHiderConfig.isEnabled();
        boolean serverIdEnabled = RenderConfig.isServerIdHiderEnabled();
        if (!nickEnabled && !serverIdEnabled) return text;
        if (text == null || text.isEmpty()) return text;

        String result = text;

        if (nickEnabled) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSession() != null) {
                String username = client.getSession().getUsername();
                if (username != null && !username.isEmpty()) {
                    result = caseInsensitiveReplace(result, username, NickHiderConfig.getNickname());
                }
            }

            for (Map.Entry<String, String> entry : NickHiderConfig.getNameMappings().entrySet()) {
                result = caseInsensitiveReplace(result, entry.getKey(), entry.getValue());
            }
        }

        if (serverIdEnabled) {
            String replacement = RenderConfig.getServerIdReplacement();
            for (String id : ServerIdTracker.getCachedIds()) {
                result = caseInsensitiveReplace(result, id, replacement);
            }
        }

        return result;
    }

    /**
     * Applies all nick-hider replacements to a StringVisitable (used by getWidth(StringVisitable)).
     * Also applies server ID hider independently.
     */
    public static StringVisitable replaceAllNamesInStringVisitable(StringVisitable text) {
        boolean nickEnabled = NickHiderConfig.isEnabled();
        boolean serverIdEnabled = RenderConfig.isServerIdHiderEnabled();
        if (!nickEnabled && !serverIdEnabled) return text;

        if (text instanceof Text t) {
            Text result = t;

            if (nickEnabled) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getSession() != null) {
                    String username = client.getSession().getUsername();
                    if (username != null && !username.isEmpty()) {
                        result = replaceLiteralTextIgnoreCase(result, username, NickHiderConfig.getNickname());
                    }
                }

                for (Map.Entry<String, String> entry : NickHiderConfig.getNameMappings().entrySet()) {
                    result = replaceLiteralTextIgnoreCase(result, entry.getKey(), entry.getValue());
                }
            }

            if (serverIdEnabled) {
                String replacement = RenderConfig.getServerIdReplacement();
                for (String id : ServerIdTracker.getCachedIds()) {
                    result = replaceLiteralTextIgnoreCase(result, id, replacement);
                }
            }

            return result;
        }
        // Non-Text StringVisitable: extract string, replace, wrap
        String content = text.getString();
        if (content == null || content.isEmpty()) return text;
        String replaced = replaceAllNamesInString(content);
        if (replaced.equals(content)) return text;
        return Text.literal(replaced);
    }

    private static String caseInsensitiveReplace(String text, String find, String replace) {
        StringBuilder result = new StringBuilder();
        String textLower = text.toLowerCase();
        String findLower = find.toLowerCase();
        int idx = 0;
        while (idx < text.length()) {
            int hit = textLower.indexOf(findLower, idx);
            if (hit < 0) {
                result.append(text.substring(idx));
                break;
            }
            result.append(text, idx, hit);
            result.append(replace);
            idx = hit + find.length();
        }
        return result.toString();
    }

    private static Text replaceLiteralTextInternal(Text original, String find, String replace, boolean ignoreCase) {
        if (original == null || find == null || replace == null || find.isEmpty()) {
            return original;
        }

        final String needle = ignoreCase ? find.toLowerCase() : find;
        final String haystack = ignoreCase ? original.getString().toLowerCase() : original.getString();
        if (!haystack.contains(needle)) return original;

        // Rebuild text tree, swapping inside literal contents only.
        MutableText result = Text.empty();
        original.visit((style, content) -> {
            if (content != null) {
                final String source = ignoreCase ? content.toLowerCase() : content;
                if (!source.contains(needle)) {
                    result.append(Text.literal(content).setStyle(style));
                    return Optional.empty();
                }
                StringBuilder sb = new StringBuilder();
                int idx = 0;
                int len = content.length();
                while (idx < len) {
                    int hit = ignoreCase
                            ? source.indexOf(needle, idx)
                            : content.indexOf(find, idx);
                    if (hit < 0) {
                        sb.append(content.substring(idx));
                        break;
                    }
                    sb.append(content, idx, hit);
                    sb.append(replace);
                    idx = hit + find.length();
                }
                String replaced = sb.toString();
                result.append(Text.literal(replaced).setStyle(style));
            }
            return Optional.empty();
        }, original.getStyle());
        // If nothing changed (strings equal), fall back to flattened replace (loses style) to catch split runs.
        if (result.getString().equals(original.getString())) {
            String flat = ignoreCase
                    ? original.getString().replaceAll("(?i)" + java.util.regex.Pattern.quote(find), replace)
                    : original.getString().replace(find, replace);
            return Text.literal(flat).setStyle(original.getStyle());
        }
        return result;
    }
}
