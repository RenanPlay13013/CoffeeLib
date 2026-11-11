package net.loyalnetwork.coffeelib.core.config.format;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigSaveException;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * YAML backend on SpongePowered Configurate ({@code configurate-yaml}, which
 * wraps SnakeYAML). Platform-agnostic: this is what
 * {@code @ConfigFile(format = YAML)} resolves to on Forge-like platforms, and
 * the same engine Paper has always used.
 * <p>
 * {@code configurate-yaml} 4.2.0's writer serializes the raw value tree and
 * drops node comments, so {@code @Comment}s are re-injected as {@code # }
 * lines above the annotated key in a post-pass over the emitted YAML. The
 * output is deterministic (block style, two-space indent, insertion order),
 * which is what makes the dotted-path to key-line mapping reliable.
 */
public final class YamlConfigBackend implements ConfigBackend {

    private static final String INDENT_UNIT = "  ";

    @Override
    public String fileExtension() {
        return "yml";
    }

    @Override
    public Map<String, Object> read(Path file) {
        try {
            return ConfigurateSupport.rootToJava(loader(file).load());
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

            Files.createDirectories(file.getParent());
            Files.writeString(file, injectComments(yaml, comments), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigSaveException("Could not write " + file, e);
        }
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .indent(2)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
    }

    /**
     * Inserts {@code # comment} lines above the key each dotted path names.
     * Block-style output is a deterministic "indent + key:" line per node, so
     * each comment targets the line at {@code (segments - 1) * 2} spaces
     * whose key is the last path segment.
     */
    private static String injectComments(String yaml, Map<String, String> comments) {
        if (comments.isEmpty()) {
            return yaml;
        }

        List<String> lines = new ArrayList<>();
        for (String line : yaml.split("\n")) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        Map<Integer, List<String>> insertions = new TreeMap<>();
        for (Map.Entry<String, String> entry : comments.entrySet()) {
            String[] path = entry.getKey().split("\\.");
            String pad = INDENT_UNIT.repeat(path.length - 1);
            int index = indexOfKeyLine(lines, pad + path[path.length - 1] + ":");
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
}
