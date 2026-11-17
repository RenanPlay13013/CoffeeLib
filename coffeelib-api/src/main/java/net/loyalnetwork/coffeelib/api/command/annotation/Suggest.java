package net.loyalnetwork.coffeelib.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tab-completion values for an argument. Enums and booleans complete on their
 * own; this is for everything else.
 * <ul>
 *   <li>{@link #value()} - a fixed list, e.g. {@code @Suggest({"common", "rare"})};</li>
 *   <li>{@link #provider()} - the name of a method on the same handler object
 *       that returns a {@code Collection<String>} and takes either nothing or
 *       the {@code CommandSource}, for values that change, such as online
 *       players or configured crate names.</li>
 * </ul>
 * Suggestions are filtered by what the sender has typed so far, so a provider
 * returns every candidate and does not filter itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Suggest {

    String[] value() default {};

    String provider() default "";
}
