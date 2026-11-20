package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cross-platform, per-owner invalidation subscriptions. LuckPerms callbacks can arrive on
 * arbitrary threads; only {@link #flushTick()} delivers, on the server thread calling it.
 * Changes queued while dispatching are held for the NEXT tick, not silently lost.
 */
public final class SharedPermissionChanges {
    private static final Logger LOG = Logger.getLogger(SharedPermissionChanges.class.getName());
    private static final Map<String, CopyOnWriteArrayList<Handle>> OWNERS = new ConcurrentHashMap<>();
    private static final PermissionChangeBuffer BUFFER = new PermissionChangeBuffer();

    private SharedPermissionChanges() { }

    public static EventSubscription subscribe(String owner, Consumer<UUID> listener) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner is blank");
        Objects.requireNonNull(listener, "listener");
        Handle handle = new Handle(owner, listener);
        OWNERS.compute(owner, (id, handles) -> {
            if (handles == null) handles = new CopyOnWriteArrayList<>();
            handles.add(handle);
            return handles;
        });
        return handle;
    }

    /** May be called from any thread, including LuckPerms' async dispatcher. */
    public static void changed(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        BUFFER.enqueue(playerId);
    }

    /** Must be called exactly once per Minecraft tick, on the actual server thread. */
    public static void flushTick() {
        final Set<UUID> ready = BUFFER.drain();
        if (ready.isEmpty()) return;
        // Snapshot the subscribers per tick. Unsubscribing mid-dispatch deactivates immediately.
        List<Handle> listeners = new ArrayList<>();
        for (CopyOnWriteArrayList<Handle> handles : OWNERS.values()) listeners.addAll(handles);
        for (UUID playerId : ready) {
            for (Handle handle : listeners) {
                if (!handle.active.get()) continue;
                try {
                    handle.listener.accept(playerId);
                } catch (Throwable error) {
                    if (error instanceof VirtualMachineError && !(error instanceof StackOverflowError)) {
                        throw (VirtualMachineError) error;
                    }
                    if (error instanceof ThreadDeath) throw (ThreadDeath) error;
                    LOG.log(Level.SEVERE, "[CoffeeLib] Permission change listener of " + handle.owner
                            + " failed for " + playerId, error);
                }
            }
        }
    }

    public static void releaseOwner(String owner) {
        OWNERS.computeIfPresent(owner, (id, handles) -> {
            handles.forEach(handle -> handle.active.set(false));
            return null;
        });
    }

    /** Resume dispatch when a server starts ticking (including an integrated server restart). */
    public static void resumeServer() {
        BUFFER.resume();
    }

    /** Stop accepting asynchronous notifications and discard anything belonging to this world. */
    public static void stopServer() {
        BUFFER.suspend();
    }

    /** Discard queued changes without deactivating listeners. */
    public static void clearPending() {
        BUFFER.clear();
    }

    /** On plugin unload / for test isolation. */
    public static void clearAll() {
        for (String owner : Set.copyOf(OWNERS.keySet())) releaseOwner(owner);
        clearPending();
    }

    private static final class Handle implements EventSubscription {
        private final String owner;
        private final Consumer<UUID> listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Handle(String owner, Consumer<UUID> listener) {
            this.owner = owner;
            this.listener = listener;
        }

        @Override public Class<?> eventType() { return UUID.class; }
        @Override public boolean isActive() { return active.get(); }
        @Override public void unsubscribe() {
            if (!active.compareAndSet(true, false)) return;
            OWNERS.computeIfPresent(owner, (id, handles) -> {
                handles.remove(this);
                return handles.isEmpty() ? null : handles;
            });
        }
    }
}
