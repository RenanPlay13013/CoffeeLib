package net.loyalnetwork.coffeelib.core;

import net.loyalnetwork.coffeelib.api.ConfigBackend;
import net.loyalnetwork.coffeelib.api.ConfigManager;
import net.loyalnetwork.coffeelib.api.exception.ConfigException;
import net.loyalnetwork.coffeelib.api.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.exception.ConfigSaveException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link ConfigManager}. Holds nothing but {@code baseDirectory} and
 * {@code backend} — no per-class cache, no registry. {@link #load} always
 * creates a fresh instance; callers are expected to hold onto what it
 * returns (the same pattern as the {@code @ConfigFile} example: load once
 * at startup, keep the reference), matching the "no global state" design so
 * two owners in the same JVM never share anything through this type.
 */
public final class DefaultConfigManager implements ConfigManager {

    private final Path baseDirectory;
    private final ConfigBackend backend;

    public DefaultConfigManager(Path baseDirectory, ConfigBackend backend) {
        this.baseDirectory = baseDirectory;
        this.backend = backend;
    }

    @Override
    public <T> T load(Class<T> type) {
        ConfigModel model = ConfigReflector.scan(type);
        T instance = instantiate(type);
        Path path = resolvePath(model);

        Map<String, Object> raw = Files.exists(path) ? backend.read(path) : Map.of();
        populate(instance, model.getFields(), raw);
        write(instance, model.getFields(), path);
        return instance;
    }

    @Override
    public void reload(Object config) {
        ConfigModel model = ConfigReflector.scan(config.getClass());
        Path path = resolvePath(model);
        if (!Files.exists(path)) {
            throw new ConfigLoadException("Cannot reload, file does not exist: " + path);
        }

        Map<String, Object> raw = backend.read(path);
        populate(config, model.getFields(), raw);
        write(config, model.getFields(), path);
    }

    @Override
    public void save(Object config) {
        ConfigModel model = ConfigReflector.scan(config.getClass());
        write(config, model.getFields(), resolvePath(model));
    }

    private Path resolvePath(ConfigModel model) {
        return baseDirectory.resolve(model.getFileName() + "." + backend.fileExtension());
    }

    private <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ConfigException("Config class " + type.getName() + " must expose a no-args constructor", e);
        }
    }

    /**
     * Applies raw values onto {@code instance}'s fields, keeping the current
     * (default) value for any key missing from {@code raw}. Recurses into
     * nested config objects, instantiating them on demand when the field
     * hasn't been initialized by the owning class itself.
     */
    @SuppressWarnings("unchecked")
    private void populate(Object instance, List<FieldMetadata> fields, Map<String, Object> raw) {
        for (FieldMetadata metadata : fields) {
            if (metadata.isNested()) {
                Object nestedRaw = raw.get(metadata.getKey());
                Map<String, Object> nestedMap = nestedRaw instanceof Map ? (Map<String, Object>) nestedRaw : Map.of();
                populate(nestedInstance(instance, metadata), metadata.getChildren(), nestedMap);
                continue;
            }

            if (!raw.containsKey(metadata.getKey())) {
                continue;
            }

            Object decoded = FieldCodec.decode(raw.get(metadata.getKey()), metadata.getField().getType(), metadata.getKey());
            ConfigValidator.validate(metadata, decoded);

            try {
                metadata.getField().set(instance, decoded);
            } catch (IllegalAccessException e) {
                throw new ConfigException("Could not set field '" + metadata.getKey() + "'", e);
            }
        }
    }

    /**
     * Writes {@code instance}'s current field values back to disk —
     * first-run defaults and newly added fields go through this same path.
     * Nested objects become nested {@code Map} values; comment keys carry
     * the dotted path to the field they annotate (e.g. {@code "database.host"}),
     * per the {@link ConfigBackend} contract.
     */
    private void write(Object instance, List<FieldMetadata> fields, Path path) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> comments = new LinkedHashMap<>();
        collect(instance, fields, values, comments, "");

        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new ConfigSaveException("Could not create directory for " + path, e);
        }

        backend.write(path, values, comments);
    }

    private void collect(Object instance, List<FieldMetadata> fields, Map<String, Object> values,
                          Map<String, String> comments, String pathPrefix) {
        for (FieldMetadata metadata : fields) {
            String dottedKey = pathPrefix.isEmpty() ? metadata.getKey() : pathPrefix + "." + metadata.getKey();
            if (metadata.getComment() != null) {
                comments.put(dottedKey, metadata.getComment());
            }

            if (metadata.isNested()) {
                Map<String, Object> nestedValues = new LinkedHashMap<>();
                collect(nestedInstance(instance, metadata), metadata.getChildren(), nestedValues, comments, dottedKey);
                values.put(metadata.getKey(), nestedValues);
                continue;
            }

            Object value;
            try {
                value = metadata.getField().get(instance);
            } catch (IllegalAccessException e) {
                throw new ConfigException("Could not read field '" + metadata.getKey() + "'", e);
            }

            values.put(metadata.getKey(), FieldCodec.encode(value));
        }
    }

    /** Returns the nested config object currently held by the field, instantiating one if the field is still null. */
    private Object nestedInstance(Object owner, FieldMetadata metadata) {
        try {
            Object current = metadata.getField().get(owner);
            if (current != null) {
                return current;
            }
            Object created = instantiate(metadata.getField().getType());
            metadata.getField().set(owner, created);
            return created;
        } catch (IllegalAccessException e) {
            throw new ConfigException("Could not access nested field '" + metadata.getKey() + "'", e);
        }
    }
}
