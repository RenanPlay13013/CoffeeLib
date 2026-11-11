package net.loyalnetwork.coffeelib.core.config.format;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translation layer every Configurate-backed backend in this package shares.
 * <p>
 * The {@link net.loyalnetwork.coffeelib.api.config.ConfigBackend} contract is
 * a flat/nested {@code Map<String, Object>} tree with comments keyed by
 * dotted path; Configurate's model is a tree of {@link ConfigurationNode}s
 * with the comment attached to the node. Both directions of that translation
 * live here so the per-format classes only differ in their loader and in how
 * (or whether) their writer can emit comments.
 */
final class ConfigurateSupport {

    private ConfigurateSupport() {
    }

    /** Translates a node tree into the flat/nested maps and scalar lists {@code core} expects. */
    static Object toJava(ConfigurationNode node) {
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

    /** {@link #toJava} on a root node, narrowed to the map {@code core} reads. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> rootToJava(ConfigurationNode root) {
        Object value = toJava(root);
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    /**
     * Attaches each dotted-path comment to the node it annotates, for formats
     * whose writer emits node comments on its own (HOCON). A path with no
     * node behind it — a comment on a field the value tree doesn't carry — is
     * skipped rather than creating an empty node.
     */
    static void applyComments(CommentedConfigurationNode root, Map<String, String> comments) {
        for (Map.Entry<String, String> entry : comments.entrySet()) {
            Object[] path = entry.getKey().split("\\.");
            CommentedConfigurationNode node = root.node(path);
            if (!node.virtual()) {
                node.comment(entry.getValue());
            }
        }
    }
}
