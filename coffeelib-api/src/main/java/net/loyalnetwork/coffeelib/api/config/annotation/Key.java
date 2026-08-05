package net.loyalnetwork.coffeelib.api.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the key a field is written under. Without this, the field name
 * itself is the key — this exists only for when the Java-idiomatic field
 * name and the desired config key diverge (renaming a field later without
 * breaking existing config files on disk).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Key {

    String value();
}
