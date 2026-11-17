package net.loyalnetwork.coffeelib.core.permission;

import java.util.UUID;

/**
 * What the resolver needs to know about a subject, answered by each platform
 * in its own types. Kept this small so the resolution order - permission
 * plugin, then the declared default - is written once, in
 * {@link PermissionResolver}.
 */
public interface PermissionPlatform {

    /** The player's id, or {@code null} when the subject is not a player (console, command block). */
    UUID playerId(Object subject);

    /** Whether the subject holds operator level {@code level} (2 for ops, 4 for the console). */
    boolean isOperator(Object subject, int level);
}
