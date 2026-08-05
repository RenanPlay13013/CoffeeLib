package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigSaveException;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * YAML backend for Paper backed by SpongePowered's Configurate
 * ({@code configurate-yaml}, which itself wraps SnakeYAML). Replaces the old
 * hand-rolled {@code YamlFormat} with a real, battle-tested YAML parser and
 * writer.
 * <p>
 * The {@link ConfigBackend} contract is a flat/nested {@code Map<String,
 * Object>} tree; Configurate's own data model is a tree of
 * {@link ConfigurationNode}s, so both directions translate between the two.
 * Because the tree is built from the raw values map, configurate fully
 * round-trips nested sections and scalar lists.
 * <p>
 * {@code @Comment}s are attached to their nodes, but configurate-yaml's own
 * writer serializes the raw value tree and drops node comments, so this
 * backend re-injects them as {@code # } lines above the annotated key in a
 * post-pass over the emitted YAML. The output is deterministic (block style,
 * two-space indent, insertion order), so the dotted-path -> key line mapping
 * is reliable.
 */
final class YamlConfigBackend implements ConfigBackend {

    private static final String INDENT_UNIT = "  ";

    @Override
    public String fileExtension() {
        return "yml";
    }

    @Override
    public Map<String, Object> read(Path file) {
        try {
            Object value = toJava(loader(file).load());
            return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
        } catch (IOException e) {
            throw new ConfigLoadException("Could not read " + file, e);
        }
    }

    @Override
    public void write(Path file, Map<String, Object> values, Map<String, String> comments) {
        try {
            CommentedConfigurationNode root = CommentedConfigurationNode.root();
            root.raw(values);

            String yaml = YamlConfigurationLoader.builder()
                    .indent(2)
                    .nodeStyle(NodeStyle.BLOCK)
                    .buildAndSaveString(root);

            String withComments = injectComments(yaml, comments);

            Files.createDirectories(file.getParent());
            Files.writeString(file, withComments, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigSaveException("Could not write " + file, e);
        }
    }

    private YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .indent(2)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
    }

    /**
     * Inserts {@code # comment} lines above the key each dotted path names.
     * Configurate's block-style output is a deterministic "indent + key:"
     * line per node, so each comment targets the line at
     * {@code (segments - 1) * 2} spaces whose key is the last path segment.
     */
    private static String injectComments(String yaml, Map<String, String> comments) {
        if (comments.isEmpty()) {
            return yaml;
        }

        String[] rawLines = yaml.split("\n");
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        Map<Integer, List<String>> insertions = new TreeMap<>();
        for (Map.Entry<String, String> entry : comments.entrySet()) {
            String[] path = entry.getKey().split("\\.");
            String pad = INDENT_UNIT.repeat(path.length - 1);
            String target = pad + path[path.length - 1] + ":";

            int index = indexOfKeyLine(lines, target);
            if (index < 0) {
                continue;
            }

            List<String> commentLines = new ArrayList<>();
            for (String commentLine : entry.getValue().split("\n")) {
                commentLines.add(pad + "# " + commentLine);
            }
            insertions.computeIfAbsent(index, i -> new ArrayList<>()).addAll(commentLines);
        }

        if (insertions.isEmpty()) {
            return yaml;
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            List<String> toInsert = insertions.remove(i);
            if (toInsert != null) {
                for (String commentLine : toInsert) {
                    out.append(commentLine).append('\n');
                }
            }
            out.append(lines.get(i)).append('\n');
        }
        return out.toString();
    }

    /** Finds the first line starting with {@code target}, skipping the {@code # comment} lines themselves. */
    private static int indexOfKeyLine(List<String> lines, String target) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(target) && !lines.get(i).trim().startsWith("#")) {
                return i;
            }
        }
        return -1;
    }

    /** Translates a Configurate node tree back into the flat/nested maps and scalar lists core expects. */
    private static Object toJava(ConfigurationNode node) {
        if (node.isMap()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
                map.put(String.valueOf(entry.getKey()), toJava(entry.getValue()));
            }
            return map;
        }
        if (node.isList()) {
            List<Object> list = new ArrayList<>();
            for (ConfigurationNode child : node.childrenList()) {
                list.add(toJava(child));
            }
            return list;
        }
        return node.raw();
    }
}
