package net.loyalnetwork.coffeelib.core;

import net.loyalnetwork.coffeelib.api.annotation.Comment;
import net.loyalnetwork.coffeelib.api.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.annotation.Ignore;
import net.loyalnetwork.coffeelib.api.annotation.Key;
import net.loyalnetwork.coffeelib.api.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.annotation.Range;
import net.loyalnetwork.coffeelib.api.exception.ConfigException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/** Scans a {@code @ConfigFile} class, recursively, into a {@link ConfigModel}. */
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

                Key keyAnnotation = field.getAnnotation(Key.class);
                String key = keyAnnotation != null ? keyAnnotation.value() : field.getName();

                Comment commentAnnotation = field.getAnnotation(Comment.class);
                String comment = commentAnnotation != null ? commentAnnotation.value() : null;

                Range range = field.getAnnotation(Range.class);
                OneOf oneOf = field.getAnnotation(OneOf.class);

                if (FieldCodec.isScalar(field.getType())) {
                    fields.add(new FieldMetadata(field, key, comment, range, oneOf, null));
                    continue;
                }

                if (range != null || oneOf != null) {
                    throw new ConfigException("@Range/@OneOf cannot be applied to nested object field '"
                            + field.getName() + "' in " + type.getName());
                }

                List<FieldMetadata> children = scanFields(field.getType(), stack);
                fields.add(new FieldMetadata(field, key, comment, null, null, children));
            }
            return fields;
        } finally {
            stack.pop();
        }
    }
}
