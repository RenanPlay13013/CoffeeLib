package net.loyalnetwork.coffeelib.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a numeric field on load/reload. Loading a value outside
 * [{@link #min()}, {@link #max()}] raises a validation error instead of
 * silently clamping — a config that fails to load loudly is safer than one
 * that starts with a value the operator didn't intend.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Range {

    double min();

    double max();
}
