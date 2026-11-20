package net.loyalnetwork.coffeelib.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A Java-only value object: immutable runs with no platform dependencies. */
public final class StyledText {
    // Vanilla's sixteen legacy colors, in their well-known index order.
    private static final int[] COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final String LEGACY = "0123456789abcdef";

    public record Style(Integer rgb, boolean bold, boolean italic, boolean underline,
                        boolean strikethrough, boolean obfuscated) {
        public static final Style EMPTY = new Style(null, false, false, false, false, false);

        Style color(int value) {
            return new Style(value, false, false, false, false, false);
        }

        Style format(char code) {
            return new Style(rgb, bold || code == 'l', italic || code == 'o',
                    underline || code == 'n', strikethrough || code == 'm', obfuscated || code == 'k');
        }
    }

    public record Run(String text, Style style) {
        public Run {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
        }
    }

    private final List<Run> runs;

    private StyledText(List<Run> runs) {
        this.runs = List.copyOf(runs);
    }

    public List<Run> runs() {
        return runs;
    }

    /** Returns plain text, with valid formatting stripped. */
    public String plain() {
        StringBuilder result = new StringBuilder();
        runs.forEach(run -> result.append(run.text()));
        return result.toString();
    }

    /**
     * Renders §-coded text for legacy Bukkit APIs. RGB is converted to the
     * closest color from the sixteen-color Minecraft palette (Spigot 1.8.8
     * does not support RGB text).
     */
    public String legacy() {
        StringBuilder result = new StringBuilder();
        for (Run run : runs) {
            Style style = run.style();
            result.append('§').append(legacyColor(style.rgb()));
            if (style.bold()) result.append("§l");
            if (style.italic()) result.append("§o");
            if (style.underline()) result.append("§n");
            if (style.strikethrough()) result.append("§m");
            if (style.obfuscated()) result.append("§k");
            result.append(run.text());
        }
        return result.toString();
    }

    private static char legacyColor(Integer rgb) {
        if (rgb == null) return 'r';
        long best = Long.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < COLORS.length; i++) {
            int color = COLORS[i];
            long red = ((rgb >> 16) & 255) - ((color >> 16) & 255);
            long green = ((rgb >> 8) & 255) - ((color >> 8) & 255);
            long blue = (rgb & 255) - (color & 255);
            long distance = red * red + green * green + blue * blue;
            if (distance < best) {
                best = distance;
                bestIndex = i;
            }
        }
        return LEGACY.charAt(bestIndex);
    }

    public static StyledText parse(String input) {
        Objects.requireNonNull(input, "input");
        List<Run> runs = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\\' && i + 1 < input.length() &&
                    (input.charAt(i + 1) == '&' || input.charAt(i + 1) == '§')) {
                buffer.append(input.charAt(++i));
                continue;
            }
            if (ch != '&' && ch != '§') {
                buffer.append(ch);
                continue;
            }
            if (i + 7 < input.length() && input.charAt(i + 1) == '#') {
                int rgb = parseRgb(input, i + 2);
                if (rgb >= 0) {
                    flush(buffer, style, runs);
                    style = style.color(rgb);
                    i += 7;
                    continue;
                }
            }
            if (i + 1 >= input.length()) {
                buffer.append(ch);
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));
            int colorIndex = LEGACY.indexOf(code);
            if (colorIndex == -1 && "klmnor".indexOf(code) == -1) {
                buffer.append(ch);
                continue;
            }
            flush(buffer, style, runs);
            if (colorIndex >= 0) {
                style = style.color(COLORS[colorIndex]);
            } else if (code == 'r') {
                style = Style.EMPTY;
            } else {
                style = style.format(code);
            }
            i++;
        }
        flush(buffer, style, runs);
        return new StyledText(runs);
    }

    private static int parseRgb(String input, int start) {
        int color = 0;
        for (int i = start; i < start + 6; i++) {
            int digit = Character.digit(input.charAt(i), 16);
            if (digit < 0) return -1;
            color = (color << 4) | digit;
        }
        return color;
    }

    private static void flush(StringBuilder buffer, Style style, List<Run> runs) {
        if (buffer.length() == 0) return;
        runs.add(new Run(buffer.toString(), style));
        buffer.setLength(0);
    }
}
