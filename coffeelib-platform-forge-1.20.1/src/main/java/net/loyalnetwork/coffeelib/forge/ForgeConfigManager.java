package net.loyalnetwork.coffeelib.forge;

import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.annotation.Ignore;
import net.loyalnetwork.coffeelib.api.config.annotation.Key;
import net.loyalnetwork.coffeelib.api.config.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registers configs with Forge's own config system ({@link ForgeConfigSpec}
 * via {@link ModLoadingContext#registerConfig}) instead of serializing to a
 * file CoffeeLib alone understands — other mods, {@code /forge config}, and
 * Forge's own ConfigTracker all see a real, registered {@code ModConfig}.
 * <p>
 * Structurally the same design as the NeoForge platform's manager (they
 * share lineage), with the API's real differences: registration goes
 * through the static {@link ModLoadingContext#get()} rather than a passed
 * {@code ModContainer}, and Forge's {@link IEventBus#addListener} has no
 * {@code (Class, Consumer)} overload — a single listener registered for the
 * {@link ModConfigEvent} supertype catches {@code Loading} and
 * {@code Reloading} both, since Forge's event bus walks each event's
 * {@code ListenerList} parent chain up to its superclasses when dispatching.
 * <p>
 * {@code save()}/{@code reload()} mirror values between the POJO's fields
 * and Forge's {@link ForgeConfigSpec.ConfigValue}s; actual disk I/O and
 * {@code @Range}/{@code @OneOf}-equivalent bounds checking happen inside
 * Forge's own {@code ValueSpec}, which silently resets an out-of-bounds
 * value to its default (with a log warning) instead of throwing
 * {@code ConfigValidationException} the way {@code core} does.
 * <p>
 * Scans the annotated class itself rather than reusing {@code core}'s
 * reflector — {@code core} must stay agnostic of any single platform's
 * config concepts.
 */
final class ForgeConfigManager implements ConfigManager {

    private final List<Registration> registrations = new ArrayList<>();

    ForgeConfigManager(IEventBus modEventBus) {
        modEventBus.<ModConfigEvent>addListener(this::onConfigEvent);
    }

    @Override
    public <T> T load(Class<T> type) {
        ConfigFile configFile = type.getAnnotation(ConfigFile.class);
        if (configFile == null) {
            throw new ConfigException("Class " + type.getName() + " is not annotated with @ConfigFile");
        }

        T instance = instantiate(type);
        List<Binding> bindings = new ArrayList<>();
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        scanFields(type, instance, builder, bindings, new ArrayDeque<>());
        ForgeConfigSpec spec = builder.build();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, spec, configFile.value());
        registrations.add(new Registration(instance, spec, bindings));
        return instance;
    }

    @Override
    public void reload(Object config) {
        sync(findRegistration(config).bindings());
    }

    @Override
    public void save(Object config) {
        Registration registration = findRegistration(config);
        writeBindings(registration.bindings());
        registration.spec().save();
    }

    private void onConfigEvent(ModConfigEvent event) {
        for (Registration registration : registrations) {
            if (event.getConfig().getSpec() == registration.spec()) {
                sync(registration.bindings());
                return;
            }
        }
    }

    private Registration findRegistration(Object config) {
        for (Registration registration : registrations) {
            if (registration.instance() == config) {
                return registration;
            }
        }
        throw new ConfigException("Not a config instance produced by this manager: " + config.getClass().getName());
    }

    private void scanFields(Class<?> type, Object instance, ForgeConfigSpec.Builder builder,
                             List<Binding> bindings, Deque<Class<?>> stack) {
        if (stack.contains(type)) {
            throw new ConfigException("Circular nesting detected: "
                    + stack.stream().map(Class::getSimpleName).collect(Collectors.joining(" -> "))
                    + " -> " + type.getSimpleName());
        }

        stack.push(type);
        try {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic() || field.isAnnotationPresent(Ignore.class)) {
                    continue;
                }
                field.setAccessible(true);

                Key keyAnnotation = field.getAnnotation(Key.class);
                String key = keyAnnotation != null ? keyAnnotation.value() : field.getName();

                Comment commentAnnotation = field.getAnnotation(Comment.class);
                if (commentAnnotation != null) {
                    builder.comment(commentAnnotation.value());
                }

                Range range = field.getAnnotation(Range.class);
                OneOf oneOf = field.getAnnotation(OneOf.class);
                Class<?> fieldType = field.getType();

                if (!isScalar(fieldType)) {
                    if (range != null || oneOf != null) {
                        throw new ConfigException("@Range/@OneOf cannot be applied to nested object field '"
                                + field.getName() + "' in " + type.getName());
                    }
                    Object nested = nestedInstance(instance, field);
                    builder.push(key);
                    scanFields(fieldType, nested, builder, bindings, stack);
                    builder.pop();
                    continue;
                }

                Object defaultValue = readField(instance, field);
                ForgeConfigSpec.ConfigValue<?> configValue = defineValue(builder, key, fieldType, defaultValue, range, oneOf);
                bindings.add(new Binding(instance, field, castValue(configValue)));
            }
        } finally {
            stack.pop();
        }
    }

    @SuppressWarnings("unchecked")
    private ForgeConfigSpec.ConfigValue<?> defineValue(ForgeConfigSpec.Builder builder, String key, Class<?> fieldType,
                                                         Object defaultValue, Range range, OneOf oneOf) {
        if (fieldType == String.class) {
            // Arrays.asList, not List.of — Forge's own correction pass calls .contains(rawValue)
            // with the *uncorrected* value while validating, which is null for a key missing from
            // the file; List.of(...).contains(null) throws NPE.
            return oneOf != null
                    ? builder.defineInList(key, (String) defaultValue, Arrays.asList(oneOf.value()))
                    : builder.define(key, (String) defaultValue);
        }
        if (fieldType == boolean.class || fieldType == Boolean.class) {
            return builder.define(key, (Boolean) defaultValue);
        }
        if (fieldType.isEnum()) {
            return builder.defineEnum(key, (Enum) defaultValue);
        }
        if (fieldType == int.class || fieldType == Integer.class) {
            return range != null
                    ? builder.defineInRange(key, (Integer) defaultValue, (int) range.min(), (int) range.max())
                    : builder.<Integer>define(key, (Integer) defaultValue);
        }
        if (fieldType == long.class || fieldType == Long.class) {
            return range != null
                    ? builder.defineInRange(key, (Long) defaultValue, (long) range.min(), (long) range.max())
                    : builder.<Long>define(key, (Long) defaultValue);
        }
        if (fieldType == double.class || fieldType == Double.class) {
            return range != null
                    ? builder.defineInRange(key, (Double) defaultValue, range.min(), range.max())
                    : builder.<Double>define(key, (Double) defaultValue);
        }
        if (fieldType == float.class || fieldType == Float.class) {
            return range != null
                    ? builder.defineInRange(key, (Float) defaultValue, (float) range.min(), (float) range.max(), Float.class)
                    : builder.<Float>define(key, (Float) defaultValue);
        }
        if (fieldType == short.class || fieldType == Short.class) {
            return range != null
                    ? builder.defineInRange(key, (Short) defaultValue, (short) range.min(), (short) range.max(), Short.class)
                    : builder.<Short>define(key, (Short) defaultValue);
        }
        throw new ConfigException("Unsupported field type " + fieldType.getName() + " for key '" + key + "'");
    }

    private static void sync(List<Binding> bindings) {
        for (Binding binding : bindings) {
            try {
                binding.field().set(binding.owner(), binding.configValue().get());
            } catch (IllegalAccessException e) {
                throw new ConfigException("Could not set field '" + binding.field().getName() + "'", e);
            }
        }
    }

    private static void writeBindings(List<Binding> bindings) {
        for (Binding binding : bindings) {
            try {
                binding.configValue().set(binding.field().get(binding.owner()));
            } catch (IllegalAccessException e) {
                throw new ConfigException("Could not read field '" + binding.field().getName() + "'", e);
            }
        }
    }

    private static Object readField(Object instance, Field field) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new ConfigException("Could not read field '" + field.getName() + "'", e);
        }
    }

    private static Object nestedInstance(Object owner, Field field) {
        Object current = readField(owner, field);
        if (current != null) {
            return current;
        }
        Object created = instantiate(field.getType());
        try {
            field.set(owner, created);
        } catch (IllegalAccessException e) {
            throw new ConfigException("Could not set nested field '" + field.getName() + "'", e);
        }
        return created;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ConfigException("Config class " + type.getName() + " must expose a no-args constructor", e);
        }
    }

    private static boolean isScalar(Class<?> type) {
        return type == String.class
                || type == boolean.class || type == Boolean.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == short.class || type == Short.class
                || type.isEnum();
    }

    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<Object> castValue(ForgeConfigSpec.ConfigValue<?> value) {
        return (ForgeConfigSpec.ConfigValue<Object>) value;
    }

    private record Binding(Object owner, Field field, ForgeConfigSpec.ConfigValue<Object> configValue) {
    }

    private record Registration(Object instance, ForgeConfigSpec spec, List<Binding> bindings) {
    }
}
