package net.loyalnetwork.coffeelib.api.event;

/**
 * Order in which handlers for the same event run, from first to last.
 * <p>
 * The scale reads like Bukkit's on purpose, since that is the one most people
 * coming to this library already know: the early slots are for handlers that
 * want to shape or cancel an event, the late ones for handlers that want to
 * see what everyone else decided. {@link #MONITOR} is by convention
 * observe-only — logging, metrics, reacting to the outcome — and should not
 * change the event or cancel it, because nothing after it can react to that.
 * <p>
 * Handlers at the same priority run in registration order.
 */
public enum EventPriority {

    FIRST,
    EARLY,
    NORMAL,
    LATE,
    LAST,

    /** Runs after everything else; for watching the outcome, not changing it. */
    MONITOR
}
