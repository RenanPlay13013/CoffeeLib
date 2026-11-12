package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.annotation.Ignore;
import net.loyalnetwork.coffeelib.api.config.annotation.Key;
import net.loyalnetwork.coffeelib.api.config.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scans a {@code @ConfigFile} class, recursively, into a {@link ConfigModel}.
 * <p>
 * Every field is classified into a {@link FieldMetadata.Kind} or rejected by
 * name. The rejection is the important half: classifying by declared class
 * alone and treating "not a scalar" as "a nested object" meant a
 * {@code List<String>} recursed into the {@code java.util.List} interface,
 * found no fields, and was written out as an empty mapping — destroying an
 * operator's data with nothing logged — while a {@code Byte} field recursed
 * into {@code java.lang.Byte} and died on {@code setAccessible}, taking the
 * server down with a JPMS error. A shape this class cannot represent now
 * fails here, at scan time, naming the field and saying what to use instead.
 */
final class ConfigReflector {

    private ConfigReflector() {
    }

    static ConfigModel scan(Class<?> type) {
        ConfigFile configFile = type.getAnnotation(ConfigFile.class);
        if (configFile == null) {
            throw new ConfigException("Class " + type.getName() + " is not annotated with @ConfigFile");
        }

        List<FieldMetadata> fields = scanFields(type, new ArrayDeque<>());
        return new ConfigModel(configFile.value(), fields);
    }

    private static List<FieldMetadata> scanFields(Class<?> type, Deque<Class<?>> stack) {
        if (stack.contains(type)) {
            throw new ConfigException("Circular nesting detected: "
                    + stack.stream().map(Class::getSimpleName).collect(Collectors.joining(" -> "))
                    + " -> " + type.getSimpleName());
        }

        stack.push(type);
        try {
            List<FieldMetadata> fields = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic() || field.isAnnotationPresent(Ignore.class)) {
                    continue;
                }

                field.setAccessible(true);
                fields.add(describe(field, type, stack));
            }
            return fields;
        } finally {
            stack.pop();
        }
    }

    private static FieldMetadata describe(Field field, Class<?> owner, Deque<Class<?>> stack) {
        Key keyAnnotation = field.getAnnotation(Key.class);
        String key = keyAnnotation != null ? keyAnnotation.value() : field.getName();

        Comment commentAnnotation = field.getAnnotation(Comment.class);
        String comment = commentAnnotation != null ? commentAnnotation.value() : null;

        Range range = field.getAnnotation(Range.class);
        OneOf oneOf = field.getAnnotation(OneOf.class);
        Class<?> fieldType = field.getType();

        if (FieldCodec.isScalar(fieldType)) {
            return FieldMetadata.scalar(field, key, comment, range, oneOf);
        }

        // @Range/@OneOf constrain a single value; on anything else they would silently
        // do nothing, so say so rather than accepting an annotation that has no effect.
        if (range != null || oneOf != null) {
            throw new ConfigException("@Range/@OneOf only apply to scalar fields, not to '"
                    + field.getName() + "' (" + fieldType.getSimpleName() + ") in " + owner.getName());
        }

        if (fieldType.isArray()) {
            throw new ConfigException("Field '" + field.getName() + "' in " + owner.getName()
                    + " is an array, which CoffeeLib does not support - declare it as a List<"
                    + fieldType.getComponentType().getSimpleName() + "> instead");
        }

        if (Collection.class.isAssignableFrom(fieldType)) {
            return describeCollection(field, owner, key, comment, stack);
        }

        if (Map.class.isAssignableFrom(fieldType)) {
            return describeMap(field, owner, key, comment, stack);
        }

        requireConfigObject(field, owner, fieldType);
        return FieldMetadata.nested(field, key, comment, scanFields(fieldType, stack));
    }

    private static FieldMetadata describeCollection(Field field, Class<?> owner, String key, String comment,
                                                     Deque<Class<?>> stack) {
        Class<?> elementType = typeArgument(field, owner, 0, "element");
        if (FieldCodec.isScalar(elementType)) {
            return FieldMetadata.collection(field, key, comment, elementType, null);
        }
        requireConfigObject(field, owner, elementType);
        return FieldMetadata.collection(field, key, comment, elementType, scanFields(elementType, stack));
    }

    private static FieldMetadata describeMap(Field field, Class<?> owner, String key, String comment,
                                              Deque<Class<?>> stack) {
        Class<?> keyType = typeArgument(field, owner, 0, "key");
        if (keyType != String.class) {
            // A config file's keys are text; anything else would have to be stringified
            // on write and parsed back on read, with no way to say what went wrong.
            throw new ConfigException("Map field '" + field.getName() + "' in " + owner.getName()
                    + " must be keyed by String, not " + keyType.getSimpleName());
        }

        Class<?> valueType = typeArgument(field, owner, 1, "value");
        if (FieldCodec.isScalar(valueType)) {
            return FieldMetadata.map(field, key, comment, valueType, null);
        }
        requireConfigObject(field, owner, valueType);
        return FieldMetadata.map(field, key, comment, valueType, scanFields(valueType, stack));
    }

    /**
     * Reads one type argument off the field's generic signature — the part the
     * old classification ignored entirely, which is why a {@code List<String>}
     * could not work: the declared type is the {@code List} interface, and the
     * element type only exists here.
     */
    private static Class<?> typeArgument(Field field, Class<?> owner, int index, String role) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            throw new ConfigException("Field '" + field.getName() + "' in " + owner.getName()
                    + " must declare its type arguments (for example List<String> rather than a raw "
                    + field.getType().getSimpleName() + ")");
        }

        Type argument = parameterized.getActualTypeArguments()[index];
        if (!(argument instanceof Class<?> resolved)) {
            throw new ConfigException("Field '" + field.getName() + "' in " + owner.getName() + " has a "
                    + role + " type CoffeeLib cannot resolve (" + argument.getTypeName()
                    + "); use a concrete type, not a wildcard, a type variable or a nested collection");
        }
        return resolved;
    }

    /**
     * Whether {@code candidate} is something this library can instantiate and
     * walk as a config object.
     * <p>
     * The JDK check is what stops the whole class of crash the bug report hit:
     * recursing into a JDK type reaches private fields in a module that does
     * not open its packages, and {@code setAccessible} throws an
     * {@code InaccessibleObjectException} nothing here can recover from. These
     * are also exactly the types that are either scalars already or need a
     * codec, never config objects.
     */
    private static void requireConfigObject(Field field, Class<?> owner, Class<?> candidate) {
        String where = "'" + field.getName() + "' in " + owner.getName();
        String name = candidate.getName();

        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("com.sun.")) {
            throw new ConfigException("Field " + where + " is a " + candidate.getSimpleName()
                    + ", which CoffeeLib has no codec for. Supported: String, enums, every primitive and its "
                    + "wrapper, nested config objects, List/Set of those, and Map<String, those>.");
        }
        if (candidate.isInterface() || Modifier.isAbstract(candidate.getModifiers())) {
            throw new ConfigException("Field " + where + " is " + candidate.getSimpleName()
                    + ", which cannot be instantiated - a config object has to be a concrete class with a "
                    + "no-args constructor");
        }
        if (candidate.isMemberClass() && !Modifier.isStatic(candidate.getModifiers())) {
            throw new ConfigException("Field " + where + " is a non-static inner class ("
                    + candidate.getSimpleName() + "), which cannot be instantiated on its own - make it static");
        }
    }
}
