package net.loyalnetwork.coffeelib.api.event;

import net.loyalnetwork.coffeelib.api.event.exception.EventDispatchException;

import java.util.List;

/**
 * What came of delivering one event: the event itself, how many handlers ran,
 * and which of them failed.
 * <p>
 * {@link EventBus#post(Object)} deliberately tells the publisher nothing about
 * handler failures - they are logged and delivery carries on, so a notification
 * cannot be broken by someone else's bug. A publisher that is coordinating work
 * ("deliver the reward, <em>then</em> complete the session") cannot live with
 * that, and asks through {@link EventBus#dispatch(Object)} instead.
 * <p>
 * Handlers that ran and failed are counted in {@link #handlersInvoked()}.
 * Handlers skipped because the event was already cancelled, or because a
 * {@link FailurePolicy#STOP} stopped delivery, are not.
 * <p>
 * Nothing here undoes what a handler did before it threw. An event is a plain
 * object, and a handler that cancelled it or changed a field and then failed
 * leaves it cancelled or changed; the bus has no transaction to roll back.
 * A publisher that needs all-or-nothing has to arrange that itself, and
 * {@link #hasFailures()} is how it finds out it must.
 *
 * @param <T> the event type
 */
public record EventResult<T>(T event, int handlersInvoked, List<EventFailure> failures) {

    public EventResult {
        failures = List.copyOf(failures);
    }

    /** Whether any handler threw. */
    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /** Whether the event is a {@link Cancellable} that ended up cancelled. */
    public boolean isCancelled() {
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    /**
     * Returns this result, or throws if any handler failed.
     * <p>
     * For a publisher that wants failures to be exceptions after all, at the
     * point it chooses rather than for every event: the first failure is the
     * exception's cause and the rest are attached as suppressed.
     *
     * @throws EventDispatchException if {@link #hasFailures()}
     */
    public EventResult<T> throwIfFailed() {
        if (hasFailures()) {
            throw new EventDispatchException(event, failures);
        }
        return this;
    }
}
