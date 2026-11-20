package net.loyalnetwork.coffeelib.api.permission;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.loyalnetwork.coffeelib.api.event.EventSubscription;

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

    /**
     * Notifies this owner's subscriber when the effective permissions of a player MAY have changed.
     * Callbacks run on the server's tick thread (never on LuckPerms' asynchronous event thread).
     * Multiple changes to the same UUID in one tick are coalesced; the callback must recheck
     * the nodes it cares about via {@link #has(Object, String)}. This is an invalidation signal,
     * not a guaranteed diff, and changes not exposed by a platform/provider can go unnoticed.
     * On Paper without LuckPerms, a central check runs at most once every 20 ticks for operator
     * status and nodes declared to CoffeeLib. Offline players are not polled.
     *
     * <p>Unsubscribe when no longer needed. Paper also drops all of the owner's subscriptions
     * when its plugin is disabled. Forge/NeoForge subscriptions last for the lifetime of the mod.
     *
     * @return a cancellable subscription; its event type is {@code UUID.class}
     */
    default EventSubscription onChange(Consumer<UUID> listener) {
        throw new UnsupportedOperationException("Permission-change notifications not implemented by " + getClass().getName());
    }

    /**
     * Declares the inclusive numeric permission range {@code prefix.N} with NOBODY as
     * the default. Grants must come from a permission provider or explicit server
     * permission configuration. To avoid massive registries, limit the range to
     * 10,000 entries. Invalid prefixes and ranges fail before registration.
     */
    default void declareRange(String prefix, int first, int last) {
        throw new UnsupportedOperationException("Numeric permission ranges not implemented by " + getClass().getName());
    }

    /**
     * The highest effective non-negative numeric permission {@code prefix.N}.
     * With LuckPerms the integer meta value at {@code prefix} takes precedence;
     * otherwise all granted nodes in LuckPerms' contextual permission map are
     * considered. Without LuckPerms, only nodes declared through this manager
     * are considered (using {@link #has}); {@code fallback} is returned if none
     * match. Never loads offline permission data from storage, and never blocks.
     */
    default int number(Object subject, String prefix, int fallback) {
        throw new UnsupportedOperationException("Numeric permissions not implemented by " + getClass().getName());
    }

    /** The nodes this manager declared. */
    Set<String> declared();
}
