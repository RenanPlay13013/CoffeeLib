package net.loyalnetwork.coffeelib.api.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a config model. {@link #value()} is the file name (without
 * extension) — each backend appends its own extension (.yml, .toml, ...).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigFile {

    String value();
}
