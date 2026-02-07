package floydaddons.not.dogshit.client;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

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
