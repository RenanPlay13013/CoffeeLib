package net.loyalnetwork.coffeelib.core.permission;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-lifecycle mailbox for asynchronous permission invalidations.
 * A drain exchanges sets, so updates enqueued during notification are
 * delivered on the next tick instead of being lost.
 */
final class PermissionChangeBuffer {
    private Set<UUID> pending = new HashSet<>();
    private boolean accepting = true;

    synchronized void enqueue(UUID player) {
        Objects.requireNonNull(player, "player");
        if (accepting) pending.add(player);
    }

    synchronized Set<UUID> drain() {
        Set<UUID> batch = pending;
        pending = new HashSet<>();
        return batch;
    }

    synchronized void suspend() {
        accepting = false;
        pending.clear();
    }

    synchronized void resume() { accepting = true; }
    synchronized void clear() { pending.clear(); }
}
