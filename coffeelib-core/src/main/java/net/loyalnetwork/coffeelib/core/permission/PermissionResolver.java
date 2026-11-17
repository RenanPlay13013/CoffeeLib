package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;

import java.util.UUID;
import java.util.function.BiFunction;

/**
 * The resolution order, once, for the platforms that have no permission
 * system of their own to lean on (Forge and NeoForge):
 * <ol>
 *   <li>an empty node is granted;</li>
 *   <li>a subject that is not a player - the console, a command block - has
 *       everything its operator level allows, which for the console is
 *       everything;</li>
 *   <li>the platform's own permission API, for a node it knows and a handler
 *       that is not just the platform default (Forge/NeoForge's PermissionAPI
 *       with LuckPerms or another permission mod as its handler);</li>
 *   <li>a permission plugin's explicit answer wins (LuckPerms, when present);</li>
 *   <li>otherwise the node's declared default decides, with
 *       {@link PermissionDefault#OPERATORS} meaning the given op level.</li>
 * </ol>
 * The permission-plugin step is pluggable so tests can stand in for LuckPerms.
 */
public final class PermissionResolver {

    private final PermissionNodes nodes;
    private final PermissionPlatform platform;
    private final BiFunction<UUID, String, Boolean> pluginCheck;
    private final BiFunction<Object, String, Boolean> nativeCheck;

    public PermissionResolver(PermissionNodes nodes, PermissionPlatform platform) {
        this(nodes, platform, LuckPermsBridge::check);
    }

    public PermissionResolver(PermissionNodes nodes, PermissionPlatform platform,
                              BiFunction<UUID, String, Boolean> pluginCheck) {
        this(nodes, platform, pluginCheck, (subject, node) -> null);
    }

    /**
     * @param nativeCheck the platform permission API's answer for a player and
     *                    node, or {@code null} when it has none to give - node
     *                    not registered there, no real handler, not a player
     */
    public PermissionResolver(PermissionNodes nodes, PermissionPlatform platform,
                              BiFunction<UUID, String, Boolean> pluginCheck,
                              BiFunction<Object, String, Boolean> nativeCheck) {
        this.nodes = nodes;
        this.platform = platform;
        this.pluginCheck = pluginCheck;
        this.nativeCheck = nativeCheck;
    }

    public PermissionNodes nodes() {
        return nodes;
    }

    /** {@link #has(Object, String, int)} with operators meaning the node's declared op level. */
    public boolean has(Object subject, String node) {
        return has(subject, node, nodes.operatorLevelOf(node));
    }

    /**
     * @param operatorLevel the op level {@link PermissionDefault#OPERATORS}
     *                      stands for here - a command declared with level 4
     *                      keeps needing 4 when no permission plugin decides
     */
    public boolean has(Object subject, String node, int operatorLevel) {
        if (node == null || node.isEmpty()) {
            return true;
        }

        PermissionDefault def = nodes.defaultOf(node);
        UUID player = platform.playerId(subject);
        if (player == null) {
            return def == PermissionDefault.EVERYONE || platform.isOperator(subject, operatorLevel);
        }

        Boolean decided = nativeCheck.apply(subject, node);
        if (decided != null) {
            return decided;
        }

        decided = pluginCheck.apply(player, node);
        if (decided != null) {
            return decided;
        }

        return switch (def) {
            case EVERYONE -> true;
            case OPERATORS -> platform.isOperator(subject, operatorLevel);
            case NOBODY -> false;
        };
    }
}
