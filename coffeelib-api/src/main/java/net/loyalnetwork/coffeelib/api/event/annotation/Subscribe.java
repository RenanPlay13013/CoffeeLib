package net.loyalnetwork.coffeelib.api.event.annotation;

import net.loyalnetwork.coffeelib.api.event.Cancellable;
import net.loyalnetwork.coffeelib.api.event.EventPriority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler. The method takes exactly one parameter,
 * and that parameter's type is the event it handles — subtypes included.
 * <p>
 * The method may be private; CoffeeLib does not need it public to call it.
 * Register the declaring object with {@code EventBus#register}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    /** When this handler runs relative to the others. */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * Whether to receive an event that has already been
     * {@link Cancellable#setCancelled cancelled}, which is otherwise skipped.
     * <p>
     * Worth it for a handler that must see every event regardless of outcome —
     * logging, metrics, undoing its own earlier work — and wrong for one that
     * acts on the event, which is why it is off by default.
     */
    boolean receiveCancelled() default false;
}
