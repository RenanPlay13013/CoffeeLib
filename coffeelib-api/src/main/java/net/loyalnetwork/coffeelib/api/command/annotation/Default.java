package net.loyalnetwork.coffeelib.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes an argument optional, with {@link #value()} parsed exactly as if the
 * sender had typed it. Optional arguments must come after the required ones,
 * or {@code /cmd a b} would be ambiguous - registration refuses otherwise.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Default {

    String value();
}
