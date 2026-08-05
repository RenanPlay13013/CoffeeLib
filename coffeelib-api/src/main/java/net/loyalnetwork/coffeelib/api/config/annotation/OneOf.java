package net.loyalnetwork.coffeelib.api.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a {@code String} field to a fixed set of values. A value outside
 * {@link #value()} fails validation; the error includes the closest match
 * from this set as a suggestion (typo in a config file gets a "did you mean
 * X?" instead of a bare rejection).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneOf {

    String[] value();
}
