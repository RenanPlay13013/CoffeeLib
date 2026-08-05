package net.loyalnetwork.coffeelib.neoforge;

import net.loyalnetwork.coffeelib.api.ConfigManager;
import net.loyalnetwork.coffeelib.api.annotation.Comment;
import net.loyalnetwork.coffeelib.api.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.annotation.Ignore;
import net.loyalnetwork.coffeelib.api.annotation.Key;
import net.loyalnetwork.coffeelib.api.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.annotation.Range;
import net.loyalnetwork.coffeelib.api.exception.ConfigException;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

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
 * Registers configs with NeoForge's own config system ({@link ModConfigSpec}
 * via {@link ModContainer#registerConfig}) instead of serializing to a file
 * CoffeeLib alone understands — that's the whole point of this backend:
 * other mods, {@code /neoforge config}, and NeoForge's own ConfigTracker all
 * see a real, registered {@code ModConfig}, not a private CoffeeLib file.
 * <p>
 * A direct consequence: NeoForge, not this class, owns persistence and
 * validation. {@code save()}/{@code reload()} here mirror values between the
 * POJO's fields and NeoForge's {@link ModConfigSpec.ConfigValue}s — actual
 * disk I/O and the {@code @Range}/{@code @OneOf}-equivalent bounds checking
 * happen inside NeoForge's own {@code ValueSpec}, which silently resets an
 * out-of-bounds value to its default (with a log warning) rather than
 * throwing {@code ConfigValidationException} the way {@code core} does.
 * <p>
 * Scans the annotated class itself rather than reusing {@code core}'s
 * reflector — {@code core} must stay agnostic of any single platform's
 * config concepts, and {@link ModConfigSpec.Builder} is a fundamentally
 * different target (a stateful push/pop builder producing bound
 * {@code ConfigValue}s) than the flat {@code Map<String, Object>} tree
 * {@code core} produces for the write side.
 */
final class NeoForgeConfigManager implements ConfigManager {

    private final ModContainer container;
    private final List<Registration> registrations = new ArrayList<>();

    NeoForgeConfigManager(ModContainer container, IEventBus modEventBus) {
        this.container = container;
        modEventBus.addListener(ModConfigEvent.Loading.class, this::onConfigEvent);
        modEventBus.addListener(ModConfigEvent.Reloading.class, this::onConfigEvent);
    }

    @Override
    public <T> T load(Class<T> type) {
        ConfigFile configFile = type.getAnnotation(ConfigFile.class);
        if (configFile == null) {
            throw new ConfigException("Class " + type.getName() + " is not annotated with @ConfigFile");
        }

        T instance = instantiate(type);
        List<Binding> bindings = new ArrayList<>();
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        scanFields(type, instance, builder, bindings, new ArrayDeque<>());
        ModConfigSpec spec = builder.build();

        container.registerConfig(ModConfig.Type.COMMON, spec, configFile.value());
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

    private void scanFields(Class<?> type, Object instance, ModConfigSpec.Builder builder,
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
                ModConfigSpec.ConfigValue<?> configValue = defineValue(builder, key, fieldType, defaultValue, range, oneOf);
                bindings.add(new Binding(instance, field, castValue(configValue)));
            }
        } finally {
            stack.pop();
        }
    }

    @SuppressWarnings("unchecked")
    private ModConfigSpec.ConfigValue<?> defineValue(ModConfigSpec.Builder builder, String key, Class<?> fieldType,
                                                       Object defaultValue, Range range, OneOf oneOf) {
        if (fieldType == String.class) {
            return oneOf != null
                    // Arrays.asList, not List.of — NeoForge's own correction pass calls
                    // .contains(rawValue) with the *uncorrected* value while validating, which is
                    // null for a key missing from the file; List.of(...).contains(null) throws NPE.
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
    private static ModConfigSpec.ConfigValue<Object> castValue(ModConfigSpec.ConfigValue<?> value) {
        return (ModConfigSpec.ConfigValue<Object>) value;
    }

    private record Binding(Object owner, Field field, ModConfigSpec.ConfigValue<Object> configValue) {
    }

    private record Registration(Object instance, ModConfigSpec spec, List<Binding> bindings) {
    }
}
