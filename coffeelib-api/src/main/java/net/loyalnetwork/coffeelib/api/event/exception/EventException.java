package net.loyalnetwork.coffeelib.api.event.exception;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

/**
 * A handler that cannot be registered: an {@code @Subscribe} method whose
 * signature is not a single parameter, or one the JVM refuses to make
 * accessible.
 * <p>
 * Raised at registration, never during delivery — a handler that throws while
 * handling an event is logged and skipped instead, so one owner's bug cannot
 * take down another's handlers.
 */
public class EventException extends CoffeeLibException {

    public EventException(String message) {
        super(message);
    }

    public EventException(String message, Throwable cause) {
        super(message, cause);
    }
}
