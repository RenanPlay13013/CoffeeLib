package net.loyalnetwork.coffeelib.core.event;

import net.loyalnetwork.coffeelib.api.event.EventBus;

/**
 * The one registry every owner's bus handle shares, and the reason an event
 * posted by one mod reaches another's handlers.
 * <p>
 * Process-wide state, like {@code ConfigReloadRegistry} and for the same kind
 * of reason: a channel between owners cannot be per-owner. It holds no events
 * and queues nothing — only the handlers each owner registered, keyed by the
 * owner id its handle was created with.
 * <p>
 * <b>One copy, or no shared channel.</b> This only works while every owner
 * loads the same {@code SharedEventBus} class. On Forge and NeoForge that
 * means installing CoffeeLib as its own mod jar, which all mods then load from;
 * on Paper, depending on the CoffeeLib plugin ({@code depend}/{@code softdepend}
 * in your {@code plugin.yml}) so your plugin's class loader resolves these
 * classes from it. A mod or plugin that shades CoffeeLib into itself instead
 * gets its own copy of this class, and therefore its own private channel —
 * fine if that is what you want, silent if it is not.
 */
public final class SharedEventBus {

    private static final EventRegistry REGISTRY = new EventRegistry();

    private SharedEventBus() {
    }

    /**
     * A bus handle for {@code ownerId} onto the shared registry. Cheap, and
     * safe to call more than once: handles are stateless beyond the id, so two
     * handles for the same owner behave identically.
     */
    public static EventBus forOwner(String ownerId) {
        return new DefaultEventBus(REGISTRY, ownerId);
    }

    /**
     * Drops every handler registered under {@code ownerId}, for cleaning up an
     * owner that is going away without holding a handle to it — a Paper plugin
     * being disabled, say.
     */
    public static void unregisterAll(String ownerId) {
        REGISTRY.unregisterAll(ownerId);
    }

    /** How many handlers {@code ownerId} currently has, for diagnostics. */
    public static int handlerCount(String ownerId) {
        return REGISTRY.handlerCount(ownerId);
    }
}
