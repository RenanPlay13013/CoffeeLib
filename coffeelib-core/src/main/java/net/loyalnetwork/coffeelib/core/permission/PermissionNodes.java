package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Every declared permission node, its default and its description.
 * <p>
 * Shared by the whole process so the default an owner declared for its node
 * is the one applied wherever that node is checked - including by CoffeeLib's
 * own command system, which declares the nodes of {@code @Command} methods.
 * <p>
 * Platforms that mirror nodes into a permission system of their own -
 * Forge's PermissionAPI, Bukkit's plugin manager - {@link #addListener listen}
 * for declarations instead of polling.
 */
public final class PermissionNodes {

    /** Segments of lower case letters, digits, {@code _} and {@code -}, separated by dots; {@code *} allowed as a segment. */
    private static final Pattern NODE = Pattern.compile("([a-z0-9_-]+|\\*)(\\.([a-z0-9_-]+|\\*))*");

    /** The op level {@link PermissionDefault#OPERATORS} means unless a declaration says otherwise. */
    public static final int DEFAULT_OPERATOR_LEVEL = 2;

    private final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private final List<Consumer<NodeInfo>> listeners = new CopyOnWriteArrayList<>();

    /**
     * One declared node.
     *
     * @param operatorLevel what {@link PermissionDefault#OPERATORS} means for
     *                      this node - 2 normally, or a command's own level
     */
    public record NodeInfo(String node, String ownerId, PermissionDefault def, int operatorLevel,
                           String description) {
    }

    /** Declares or re-declares {@code node}, operators meaning op level 2. */
    public void declare(String ownerId, String node, PermissionDefault def, String description) {
        declare(ownerId, node, def, DEFAULT_OPERATOR_LEVEL, description);
    }

    /** Declares or re-declares {@code node}. */
    public void declare(String ownerId, String node, PermissionDefault def, int operatorLevel, String description) {
        requireValid(node);
        NodeInfo info = new NodeInfo(node, ownerId, def, operatorLevel, description == null ? "" : description);
        nodes.put(node, info);
        notifyListeners(info);
    }

    /**
     * Declares {@code node} only if nobody has yet - for a node implied by
     * something else, such as a command, which must not override what its
     * owner declared on purpose.
     */
    public void declareIfAbsent(String ownerId, String node, PermissionDefault def, String description) {
        declareIfAbsent(ownerId, node, def, DEFAULT_OPERATOR_LEVEL, description);
    }

    public void declareIfAbsent(String ownerId, String node, PermissionDefault def, int operatorLevel,
                                String description) {
        requireValid(node);
        NodeInfo info = new NodeInfo(node, ownerId, def, operatorLevel, description == null ? "" : description);
        if (nodes.putIfAbsent(node, info) == null) {
            notifyListeners(info);
        }
    }

    /** The default for {@code node}, or {@link PermissionDefault#OPERATORS} when it was never declared. */
    public PermissionDefault defaultOf(String node) {
        NodeInfo info = nodes.get(node);
        return info == null ? PermissionDefault.OPERATORS : info.def();
    }

    /** What "operators" means for {@code node}: its declared op level, 2 if never declared. */
    public int operatorLevelOf(String node) {
        NodeInfo info = nodes.get(node);
        return info == null ? DEFAULT_OPERATOR_LEVEL : info.operatorLevel();
    }

    public String descriptionOf(String node) {
        NodeInfo info = nodes.get(node);
        return info == null ? "" : info.description();
    }

    /** Every declared node, in no particular order. */
    public List<NodeInfo> all() {
        return new ArrayList<>(nodes.values());
    }

    /** The nodes {@code ownerId} declared, sorted. */
    public Set<String> declaredBy(String ownerId) {
        TreeSet<String> owned = new TreeSet<>();
        nodes.forEach((node, info) -> {
            if (info.ownerId().equals(ownerId)) {
                owned.add(node);
            }
        });
        return Set.copyOf(owned);
    }

    /** Told about every declaration that changed or added a node, after it is stored. */
    public void addListener(Consumer<NodeInfo> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(NodeInfo info) {
        for (Consumer<NodeInfo> listener : listeners) {
            listener.accept(info);
        }
    }

    static void requireValid(String node) {
        if (node == null || !NODE.matcher(node).matches()) {
            throw new CoffeeLibException("Permission node '" + node + "' must be dot-separated lower case "
                    + "segments of letters, digits, '_' or '-', such as 'crates.give'");
        }
    }
}
