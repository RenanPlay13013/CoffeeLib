package net.loyalnetwork.coffeelib.api.permission;

import java.util.Set;

/**
 * Permission nodes for one owner, checked the same way on every platform.
 * <p>
 * {@link #has(Object, String)} asks LuckPerms first when it is installed,
 * and falls back to the node's declared {@link PermissionDefault} when
 * LuckPerms has no answer - or when there is no LuckPerms at all, which is
 * what makes a mod's permissions work on a server without a permission
 * plugin. On Paper the answer comes from Bukkit's own permission system,
 * which LuckPerms already plugs into.
 * <p>
 * Nodes are plain strings, e.g. {@code crates.give}. A node that was never
 * declared defaults to {@link PermissionDefault#OPERATORS}.
 */
public interface PermissionManager {

    /** Declares {@code node} with who has it by default. Declaring it again updates both. */
    void declare(String node, PermissionDefault def, String description);

    /** Declares every {@code @Permission} {@code static final String} field of {@code holder}. */
    void declare(Class<?> holder);

    /**
     * Whether {@code subject} has {@code node}. The subject is the platform's
     * own type: a {@code ServerPlayer} or {@code CommandSourceStack} on
     * Forge/NeoForge, a {@code CommandSender} or {@code Player} on Paper, or a
     * CoffeeLib {@code CommandSource}. An empty node is always granted.
     */
    boolean has(Object subject, String node);

    /** The nodes this manager declared. */
    Set<String> declared();
}
