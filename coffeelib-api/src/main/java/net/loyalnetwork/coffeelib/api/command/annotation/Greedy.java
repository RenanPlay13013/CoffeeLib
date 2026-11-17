package net.loyalnetwork.coffeelib.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a {@code String} argument: take the rest of the input, spaces included,
 * as in {@code /broadcast <message...>}. Has to be the last argument.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Greedy {
}
