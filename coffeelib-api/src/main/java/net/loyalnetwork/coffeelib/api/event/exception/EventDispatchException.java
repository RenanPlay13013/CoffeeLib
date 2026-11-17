package net.loyalnetwork.coffeelib.api.event.exception;

import net.loyalnetwork.coffeelib.api.event.EventFailure;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised by {@link net.loyalnetwork.coffeelib.api.event.EventResult#throwIfFailed()}
 * when one or more handlers threw while an event was delivered.
 * <p>
 * The first failure is the cause and any further ones are suppressed, so a
 * stack trace shows every handler that failed, each with its own trace and
 * none of them re-wrapped.
 */
public class EventDispatchException extends EventException {

    private final transient List<EventFailure> failures;

    public EventDispatchException(Object event, List<EventFailure> failures) {
        super(describe(event, failures), failures.get(0).cause());
        this.failures = List.copyOf(failures);
        for (int i = 1; i < failures.size(); i++) {
            addSuppressed(failures.get(i).cause());
        }
    }

    /** Every handler that failed, in the order it ran. */
    public List<EventFailure> failures() {
        return failures;
    }

    private static String describe(Object event, List<EventFailure> failures) {
        String who = failures.stream()
                .map(failure -> failure.ownerId() + " (" + failure.cause().getClass().getSimpleName() + ")")
                .collect(Collectors.joining(", "));
        return failures.size() + " event handler" + (failures.size() == 1 ? "" : "s") + " failed while handling "
                + event.getClass().getName() + ": " + who;
    }
}
