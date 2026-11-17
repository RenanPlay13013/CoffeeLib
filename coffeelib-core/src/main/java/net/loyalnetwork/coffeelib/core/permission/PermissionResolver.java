package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;

import java.util.List;
import java.util.UUID;

/**
 * The resolution order, once, for the platforms that have no permission
 * system of their own to lean on (Forge and NeoForge):
 * <ol>
 *   <li>an empty node is granted;</li>
 *   <li>a subject that is not a player - the console, a command block - has
 *       everything its operator level allows, which for the console is
 *       everything;</li>
 *   <li>the {@link PermissionSource sources}, in order - on Forge and
 *       NeoForge the platform's PermissionAPI, then LuckPerms asked
 *       directly - and the first one with an answer decides;</li>
 *   <li>otherwise the node's declared default decides, with
 *       {@link PermissionDefault#OPERATORS} meaning the given op level.</li>
 * </ol>
 * Sources are pluggable so tests can stand in for the platform and LuckPerms.
 */
public final class PermissionResolver {

    private final PermissionNodes nodes;
    private final PermissionPlatform platform;
    private final List<PermissionSource> sources;

    public PermissionResolver(PermissionNodes nodes, PermissionPlatform platform, PermissionSource... sources) {
        this.nodes = nodes;
        this.platform = platform;
        this.sources = List.of(sources);
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

        for (PermissionSource source : sources) {
            Boolean decided = source.check(subject, player, node);
            if (decided != null) {
                return decided;
            }
        }

        return switch (def) {
            case EVERYONE -> true;
            case OPERATORS -> platform.isOperator(subject, operatorLevel);
            case NOBODY -> false;
        };
    }
}
