package net.loyalnetwork.coffeelib.api.event;

/**
 * Handle to one subscription made with
 * {@link EventBus#subscribe(Class, java.util.function.Consumer)}, for undoing
 * exactly that one.
 * <p>
 * A lambda has no identity a caller can hand back later, unlike the object
 * {@link EventBus#register} takes, which is why subscribing returns this
 * instead.
 */
public interface EventSubscription {

    /** The event type this subscription listens for. */
    Class<?> eventType();

    /** Whether it is still receiving events. */
    boolean isActive();

    /** Stops it. Calling this more than once does nothing further. */
    void unsubscribe();
}
