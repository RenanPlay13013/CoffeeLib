package net.loyalnetwork.coffeelib.paper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The indented {@code key: value} dialect {@link YamlConfigBackend} reads
 * and writes. Not a general-purpose YAML parser — two spaces per nesting
 * level, comments on their own {@code # ...} lines, section headers as a
 * bare {@code key:} with no value on the line. Sufficient for what
 * {@code core} produces (arbitrarily deep {@code Map<String, Object>} trees
 * of scalars), nothing more.
 */
final class YamlFormat {

    private static final String INDENT_UNIT = "  ";

    private YamlFormat() {
    }

    static String format(Map<String, Object> values, Map<String, String> comments) {
        StringBuilder out = new StringBuilder();
        writeSection(out, values, comments, "", 0);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeSection(StringBuilder out, Map<String, Object> values, Map<String, String> comments,
                                      String pathPrefix, int indentLevel) {
        String pad = INDENT_UNIT.repeat(indentLevel);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String dottedKey = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;

            String comment = comments.get(dottedKey);
            if (comment != null) {
                for (String line : comment.split("\n")) {
                    out.append(pad).append("# ").append(line).append('\n');
                }
            }

            if (value instanceof Map<?, ?>) {
                out.append(pad).append(key).append(":\n");
                writeSection(out, (Map<String, Object>) value, comments, dottedKey, indentLevel + 1);
            } else {
                out.append(pad).append(key).append(": ").append(encodeScalar(value)).append('\n');
            }
        }
    }

    static Map<String, Object> parse(List<String> lines) {
        Map<String, Object> root = new LinkedHashMap<>();
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(-1, root));

        for (String line : lines) {
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }

            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();

            while (stack.size() > 1 && indent <= stack.peek().indent) {
                stack.pop();
            }
            Map<String, Object> current = stack.peek().map;

            if (trimmed.endsWith(":")) {
                String key = trimmed.substring(0, trimmed.length() - 1);
                Map<String, Object> section = new LinkedHashMap<>();
                current.put(key, section);
                stack.push(new Frame(indent, section));
                continue;
            }

            int separator = trimmed.indexOf(": ");
            if (separator < 0) {
                continue;
            }
            String key = trimmed.substring(0, separator);
            String rawValue = trimmed.substring(separator + 2);
            current.put(key, decodeScalar(rawValue));
        }

        return root;
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static Object decodeScalar(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        if (raw.equals("true") || raw.equals("false")) {
            return Boolean.parseBoolean(raw);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            // not an integer, fall through
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            // not a decimal either, treat as a plain string
        }
        return raw;
    }

    private static String encodeScalar(Object value) {
        if (value == null) {
            return "\"\"";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }

        String str = value.toString();
        return needsQuoting(str) ? quote(str) : str;
    }

    private static boolean needsQuoting(String str) {
        if (str.isEmpty() || str.equals("true") || str.equals("false") || !str.equals(str.trim())) {
            return true;
        }
        if (str.indexOf(':') >= 0 || str.indexOf('#') >= 0) {
            return true;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String quote(String str) {
        return '"' + str.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static final class Frame {
        final int indent;
        final Map<String, Object> map;

        Frame(int indent, Map<String, Object> map) {
            this.indent = indent;
            this.map = map;
        }
    }
}
