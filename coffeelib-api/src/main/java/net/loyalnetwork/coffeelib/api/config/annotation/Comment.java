package net.loyalnetwork.coffeelib.api.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches a comment to a field in the generated config file.
 * <p>
 * Not every backend can guarantee this is preserved on write — a backend that
 * cannot represent comments in its format is allowed to ignore this
 * annotation rather than fail.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Comment {

    String value();
}
