package net.loyalnetwork.coffeelib.core.permission;

import java.util.UUID;

/**
 * Something that may decide a player's permission: the platform's permission
 * API, or a permission mod asked directly. {@link PermissionResolver} asks its
 * sources in order and the first answer wins.
 */
@FunctionalInterface
public interface PermissionSource {

    /**
     * @param subject what is being checked, as the platform gave it
     * @param player  the subject's player id, never {@code null}
     * @return {@code true} or {@code false} when this source decides,
     *         {@code null} when it has no answer
     */
    Boolean check(Object subject, UUID player, String node);
}
