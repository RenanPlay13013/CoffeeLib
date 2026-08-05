package net.loyalnetwork.coffeelib.core.config;

import lombok.Getter;
import net.loyalnetwork.coffeelib.api.config.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Metadata for a single config field, resolved once during
 * {@link ConfigReflector#scan(Class)}. {@code range}/{@code oneOf} are the
 * annotation instances themselves (or {@code null}) rather than copied
 * values — no reason to duplicate what {@link Field#getAnnotation} already
 * gives us.
 * <p>
 * {@code children} is non-null exactly when this field is a nested config
 * object rather than a scalar leaf — its own fields, scanned recursively.
 * Nesting depth is unbounded (cycle detection happens in
 * {@link ConfigReflector}).
 */
@Getter
final class FieldMetadata {

    private final Field field;
    private final String key;
    private final String comment;
    private final Range range;
    private final OneOf oneOf;
    private final List<FieldMetadata> children;

    FieldMetadata(Field field, String key, String comment, Range range, OneOf oneOf, List<FieldMetadata> children) {
        this.field = field;
        this.key = key;
        this.comment = comment;
        this.range = range;
        this.oneOf = oneOf;
        this.children = children;
    }

    boolean isNested() {
        return children != null;
    }
}
