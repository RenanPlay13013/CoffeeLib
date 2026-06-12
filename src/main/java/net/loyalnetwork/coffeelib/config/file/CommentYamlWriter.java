package net.loyalnetwork.coffeelib.config.file;

public final class CommentYamlWriter {

    private CommentYamlWriter() {
    }

    public static String write(YamlNode root) {

        StringBuilder builder = new StringBuilder();

        writeNode(
                builder,
                root,
                0
        );

        return builder.toString();
    }

    private static void writeNode(
            StringBuilder builder,
            YamlNode node,
            int indent
    ) {

        String prefix = " ".repeat(indent);

        var entries = node.children().entrySet();
        int size = entries.size();
        int index = 0;

        for (var entry : entries) {

            String key = entry.getKey();
            YamlNode child = entry.getValue();

            for (String comment : child.comments()) {
                builder.append(prefix)
                        .append("# ")
                        .append(comment)
                        .append('\n');
            }

            if (child.leaf()) {

                builder.append(prefix)
                        .append(key)
                        .append(": ")
                        .append(serialize(child.value()))
                        .append('\n');

            } else {

                builder.append(prefix)
                        .append(key)
                        .append(':')
                        .append('\n');

                writeNode(
                        builder,
                        child,
                        indent + 2
                );
            }

            if (++index < size) {
                builder.append('\n');
            }
        }
    }

    private static String serialize(Object value) {

        if (value == null) {
            return "null";
        }

        if (value instanceof String string) {

            return "\""
                    + string.replace("\"", "\\\"")
                    + "\"";
        }

        if (value instanceof Character character) {

            return "\""
                    + character
                    + "\"";
        }

        return String.valueOf(value);
    }
}