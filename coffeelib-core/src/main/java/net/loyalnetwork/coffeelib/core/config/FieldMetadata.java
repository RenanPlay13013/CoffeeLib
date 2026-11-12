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
 * {@link Kind} is what the reader and the writer switch on. It exists because
 * classifying a field by its declared class alone was not enough: a
 * {@code List<String>} is not a scalar, and treating it as a nested object
 * meant recursing into the {@code java.util.List} interface, finding no
 * fields, and writing the key out as an empty mapping — silently replacing
 * whatever an operator had put there. Every shape now either has a kind or is
 * refused by name at scan time.
 */
@Getter
final class FieldMetadata {

    /** What shape this field is, and therefore how it is read and written. */
    enum Kind {

        /** A single value {@link FieldCodec} can convert on its own. */
        SCALAR,

        /** A config object of its own, written as a nested section. */
        NESTED,

        /** A {@link java.util.Collection}, written as a sequence. */
        COLLECTION,

        /** A {@code Map<String, V>}, written as a section keyed by the map's keys. */
        MAP
    }

    private final Field field;
    private final String key;
    private final String comment;
    private final Range range;
    private final OneOf oneOf;
    private final Kind kind;

    /** The nested object's own fields, for {@link Kind#NESTED}. */
    private final List<FieldMetadata> children;

    /**
     * For {@link Kind#COLLECTION} the element type; for {@link Kind#MAP} the
     * value type. Read from the field's generic signature, which is the part
     * the old classification never looked at.
     */
    private final Class<?> elementType;

    /**
     * The element or value type's own fields, when it is a config object
     * rather than a scalar — {@code null} for a collection or map of scalars.
     */
    private final List<FieldMetadata> elementChildren;

    private FieldMetadata(Field field, String key, String comment, Range range, OneOf oneOf, Kind kind,
                          List<FieldMetadata> children, Class<?> elementType, List<FieldMetadata> elementChildren) {
        this.field = field;
        this.key = key;
        this.comment = comment;
        this.range = range;
        this.oneOf = oneOf;
        this.kind = kind;
        this.children = children;
        this.elementType = elementType;
        this.elementChildren = elementChildren;
    }

    static FieldMetadata scalar(Field field, String key, String comment, Range range, OneOf oneOf) {
        return new FieldMetadata(field, key, comment, range, oneOf, Kind.SCALAR, null, null, null);
    }

    static FieldMetadata nested(Field field, String key, String comment, List<FieldMetadata> children) {
        return new FieldMetadata(field, key, comment, null, null, Kind.NESTED, children, null, null);
    }

    static FieldMetadata collection(Field field, String key, String comment, Class<?> elementType,
                                    List<FieldMetadata> elementChildren) {
        return new FieldMetadata(field, key, comment, null, null, Kind.COLLECTION, null, elementType,
                elementChildren);
    }

    static FieldMetadata map(Field field, String key, String comment, Class<?> valueType,
                             List<FieldMetadata> valueChildren) {
        return new FieldMetadata(field, key, comment, null, null, Kind.MAP, null, valueType, valueChildren);
    }

    boolean isNested() {
        return kind == Kind.NESTED;
    }
}
