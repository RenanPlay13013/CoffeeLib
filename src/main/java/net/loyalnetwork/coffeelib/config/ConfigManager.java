package net.loyalnetwork.coffeelib.config;

import net.loyalnetwork.coffeelib.config.annotation.*;
import net.loyalnetwork.coffeelib.config.file.ConfigEntry;
import net.loyalnetwork.coffeelib.config.file.ConfigFileHandle;
import net.loyalnetwork.coffeelib.config.node.ConfigValue;
import net.loyalnetwork.coffeelib.config.serializer.*;
import net.loyalnetwork.coffeelib.config.validation.RangeValidator;
import net.loyalnetwork.coffeelib.config.validation.ValidationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private final List<Class<?>> configClasses;
    private final Map<Class<?>, List<FieldData>> fieldsByClass;
    private final Map<Class<?>, List<Method>> reloadMethods;
    private final List<ConfigFileHandle> handles;

    private ConfigManager(JavaPlugin plugin, List<Class<?>> configClasses) {
        this.plugin = plugin;
        this.configClasses = List.copyOf(configClasses);
        this.fieldsByClass = new LinkedHashMap<>();
        this.reloadMethods = new LinkedHashMap<>();
        this.handles = new ArrayList<>();
    }

    public static Builder builder(JavaPlugin plugin) {
        return new Builder(plugin);
    }

    public void load() {
        ConfigBootstrap.registerDefaults();
        fieldsByClass.clear();
        reloadMethods.clear();
        handles.clear();

        for (Class<?> configClass : configClasses) {
            loadClass(configClass);
        }
    }

    public void reload() {
        for (ConfigFileHandle handle : handles) {
            handle.reload();
        }
        for (var entry : fieldsByClass.entrySet()) {
            for (FieldData data : entry.getValue()) {
                ConfigValue<?> configValue = data.configValue();
                if (configValue.isBound()) {
                    configValue.reload();
                }
            }
        }
        for (var entry : reloadMethods.entrySet()) {
            for (Method method : entry.getValue()) {
                try {
                    method.setAccessible(true);
                    method.invoke(null);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to invoke @ReloadListener: " + method);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void loadClass(Class<?> configClass) {
        ConfigFile configFile = configClass.getAnnotation(ConfigFile.class);
        if (configFile == null) {
            plugin.getLogger().warning("Skipping " + configClass + ": missing @ConfigFile annotation");
            return;
        }

        String fileName = configFile.value();
        List<FieldData> fieldDataList = new ArrayList<>();
        List<Method> methods = new ArrayList<>();

        for (Field field : configClass.getDeclaredFields()) {
            if (!ConfigValue.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);
            ConfigValue<?> configValue;
            try {
                configValue = (ConfigValue<?>) field.get(null);
            } catch (IllegalAccessException e) {
                plugin.getLogger().warning("Cannot access field " + field + ": " + e.getMessage());
                continue;
            }

            if (configValue == null) {
                continue;
            }

            String path = resolvePath(field);
            String[] comments = resolveComments(field);
            Range range = field.getAnnotation(Range.class);
            SerializeWith serializeWith = field.getAnnotation(SerializeWith.class);

            Class<?> valueType = resolveValueType(field);
            ConfigSerializer serializer = resolveSerializer(valueType, serializeWith);

            Object serializedDefault = configValue.defaultValue();
            if (serializer != null) {
                serializedDefault = serializer.serialize(configValue.defaultValue());
            }

            fieldDataList.add(new FieldData(
                    field,
                    configValue,
                    path,
                    comments,
                    range,
                    serializer,
                    valueType,
                    serializedDefault
            ));
        }

        for (Method method : configClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(ReloadListener.class)
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                methods.add(method);
            }
        }

        if (fieldDataList.isEmpty()) {
            plugin.getLogger().warning("No ConfigValue fields found in " + configClass);
            return;
        }

        List<ConfigEntry> entries = fieldDataList.stream()
                .flatMap(data -> flattenEntries(data.path(), data.serializedDefault(), data.comments()).stream())
                .toList();

        ConfigFileHandle handle = new ConfigFileHandle(
                plugin.getDataFolder(),
                fileName,
                entries
        );
        handle.load();

        fieldsByClass.put(configClass, fieldDataList);
        handles.add(handle);

        if (!methods.isEmpty()) {
            reloadMethods.put(configClass, methods);
        }

        for (FieldData data : fieldDataList) {
            Object rawValue = rawValueForKey(handle, data);
            Object value;

            if (rawValue != null && data.serializer() != null) {
                value = data.serializer().deserialize(rawValue);
            } else if (rawValue != null) {
                value = rawValue;
            } else {
                value = data.configValue().defaultValue();
            }

            if (data.range() != null && value instanceof Number number) {
                RangeValidator validator = new RangeValidator(
                        data.range().min(),
                        data.range().max()
                );
                try {
                    validator.validate(number, data.path(), fileName);
                } catch (ValidationException e) {
                    plugin.getLogger().warning(e.getMessage());
                }
            }

            ConfigValue rawCv = data.configValue();
            if (data.serializer() != null) {
                rawCv.bind(
                        data.path(),
                        handle.config(),
                        data.serializer()
                );
            } else {
                rawCv.bind(data.path(), handle.config());
            }
        }
    }

    private String resolvePath(Field field) {
        Key key = field.getAnnotation(Key.class);
        if (key != null) {
            return key.value();
        }
        return field.getName()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
    }

    private String[] resolveComments(Field field) {
        Comment comment = field.getAnnotation(Comment.class);
        return comment != null ? comment.value() : new String[0];
    }

    private Class<?> resolveValueType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType paramType) {
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> typeClass) {
                return typeClass;
            }
        }
        return Object.class;
    }

    @SuppressWarnings("unchecked")
    private <T> ConfigSerializer<T> resolveSerializer(
            Class<T> valueType,
            SerializeWith annotation
    ) {
        if (annotation != null) {
            try {
                Class<? extends ConfigSerializer<?>> serializerClass = annotation.value();
                if (EnumSerializer.class.isAssignableFrom(serializerClass) && valueType.isEnum()) {
                    return (ConfigSerializer<T>) new EnumSerializer<>((Class<? extends Enum>) valueType);
                }
                if (RecordSerializer.class.isAssignableFrom(serializerClass) && valueType.isRecord()) {
                    return (ConfigSerializer<T>) new RecordSerializer((Class<? extends Record>) valueType);
                }
                return (ConfigSerializer<T>) serializerClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate custom serializer for " + valueType, e);
            }
        }

        ConfigSerializer<T> registered = SerializerRegistry.find(valueType);
        if (registered != null) {
            return registered;
        }

        if (valueType.isEnum()) {
            return (ConfigSerializer<T>) new EnumSerializer<>((Class<? extends Enum>) valueType);
        }

        if (valueType.isRecord()) {
            return (ConfigSerializer<T>) new RecordSerializer((Class<? extends Record>) valueType);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<ConfigEntry> flattenEntries(String prefix, Object serialized, String[] comments) {
        if (serialized instanceof Map<?, ?> map) {
            List<ConfigEntry> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childPath = prefix + "." + entry.getKey();
                result.addAll(flattenEntries(childPath, entry.getValue(), null));
            }
            return result;
        }
        return List.of(new ConfigEntry(prefix, serialized, comments));
    }

    private Object rawValueForKey(ConfigFileHandle handle, FieldData data) {
        if (data.serializer() != null && data.valueType().isRecord()) {
            ConfigurationSection section = handle.config().getConfigurationSection(data.path());
            if (section != null) {
                return section.getValues(false);
            }
            return null;
        }
        return handle.config().get(data.path());
    }

    private record FieldData(
            Field field,
            ConfigValue<?> configValue,
            String path,
            String[] comments,
            Range range,
            ConfigSerializer<?> serializer,
            Class<?> valueType,
            Object serializedDefault
    ) {
    }

    public static final class Builder {

        private final JavaPlugin plugin;
        private final List<Class<?>> classes = new ArrayList<>();

        private Builder(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        public Builder register(Class<?> configClass) {
            classes.add(configClass);
            return this;
        }

        public ConfigManager build() {
            return new ConfigManager(plugin, classes);
        }
    }
}
