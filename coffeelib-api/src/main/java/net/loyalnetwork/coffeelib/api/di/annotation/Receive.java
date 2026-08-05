package net.loyalnetwork.coffeelib.api.di.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be injected from a matching {@link Provide} provider.
 * Resolution happens once, at {@code ServiceContainer#wire} time — there is
 * no lazy or deferred injection, so every provider a receiver needs must
 * already be registered by then.
 * <p>
 * {@link #value()} must match a provider's own name when more than one
 * provider exists for the field's type; leave both blank when there's only
 * one provider for that type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Receive {

    String value() default "";
}
