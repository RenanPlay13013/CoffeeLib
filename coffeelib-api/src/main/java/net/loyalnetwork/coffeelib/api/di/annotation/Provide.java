package net.loyalnetwork.coffeelib.api.di.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-args getter as a provider for its return type. Registered once
 * via {@code ServiceContainer#register}; the method runs at most once — the
 * first successful resolution caches the result, later lookups reuse it.
 * <p>
 * {@link #value()} disambiguates when more than one provider exists for the
 * same type; leave it blank when there's only one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Provide {

    String value() default "";
}
