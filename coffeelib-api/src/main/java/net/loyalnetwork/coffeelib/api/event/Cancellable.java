package net.loyalnetwork.coffeelib.api.event;

/**
 * Implemented by an event whose poster offers to call something off.
 * <p>
 * Nothing in the bus acts on a cancelled event by itself — cancelling is a
 * message to whoever posted it, and honoring it is that code's job:
 * {@code if (!bus.post(new MyEvent()).isCancelled()) { doTheThing(); }}.
 * What the bus does do is stop delivering a cancelled event to the handlers
 * that did not ask for it, so a handler only sees an event still worth
 * reacting to unless it opted in with
 * {@code @Subscribe(receiveCancelled = true)}.
 * <p>
 * Implementations are usually a plain mutable flag; the bus never calls
 * {@link #setCancelled} itself.
 */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
