package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigSaveException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Where {@code type}'s file lives under this manager's base directory,
     * extension included. Exposed because a config CoffeeLib manages itself
     * can be watched for changes (see {@code ManagedConfigService}), and a
     * watcher needs the path without duplicating the naming rule.
     *
     * @throws ConfigException if {@code type} is not a {@code @ConfigFile} class
     */
    public Path fileOf(Class<?> type) {
        return resolvePath(ConfigReflector.scan(type));
    }

    private Path resolvePath(ConfigModel model) {
        return ConfigFileNames.resolve(baseDirectory, model.getFileName(), backend.fileExtension());
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

            Object rawValue = raw.get(metadata.getKey());
            Object decoded = switch (metadata.getKind()) {
                case COLLECTION -> decodeCollection(metadata, rawValue);
                case MAP -> decodeMap(metadata, rawValue);
                default -> {
                    Object scalar = FieldCodec.decode(rawValue, metadata.getField().getType(), metadata.getKey());
                    ConfigValidator.validate(metadata, scalar);
                    yield scalar;
                }
            };

            try {
                metadata.getField().set(instance, decoded);
            } catch (IllegalAccessException e) {
                throw new ConfigException("Could not set field '" + metadata.getKey() + "'", e);
            }
        }
    }

    /**
     * Reads a sequence into a fresh collection of the field's own shape.
     * <p>
     * Fresh, and not the collection the field already holds, because the file
     * is the source of truth on load: merging into the default would leave an
     * element an operator deleted still in place. Elements that are config
     * objects get a new instance each — they have no identity worth
     * preserving, unlike a nested object, which is a single field.
     */
    private Object decodeCollection(FieldMetadata metadata, Object rawValue) {
        if (!(rawValue instanceof Collection<?> rawElements)) {
            throw new ConfigLoadException("Field '" + metadata.getKey() + "' expects a list, but the file has "
                    + describeShape(rawValue));
        }

        Collection<Object> target = newCollection(metadata.getField().getType(), rawElements.size());
        for (Object rawElement : rawElements) {
            target.add(decodeElement(metadata, rawElement, metadata.getKey()));
        }
        return target;
    }

    /** Reads a section into a {@code Map<String, V>}, keeping the file's key order. */
    @SuppressWarnings("unchecked")
    private Object decodeMap(FieldMetadata metadata, Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> rawEntries)) {
            throw new ConfigLoadException("Field '" + metadata.getKey() + "' expects a section of keys, but the "
                    + "file has " + describeShape(rawValue));
        }

        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<Object, Object>) rawEntries).entrySet()) {
            String key = String.valueOf(entry.getKey());
            target.put(key, decodeElement(metadata, entry.getValue(), metadata.getKey() + "." + key));
        }
        return target;
    }

    /** One element of a collection or one value of a map: a scalar, or a config object of its own. */
    @SuppressWarnings("unchecked")
    private Object decodeElement(FieldMetadata metadata, Object rawElement, String key) {
        if (metadata.getElementChildren() == null) {
            return FieldCodec.decode(rawElement, metadata.getElementType(), key);
        }
        if (!(rawElement instanceof Map)) {
            throw new ConfigLoadException("Field '" + key + "' expects "
                    + metadata.getElementType().getSimpleName() + " entries, but the file has "
                    + describeShape(rawElement));
        }

        Object element = instantiate(metadata.getElementType());
        populate(element, metadata.getElementChildren(), (Map<String, Object>) rawElement);
        return element;
    }

    /**
     * A collection matching what the field is declared as, so assigning it back
     * cannot fail: a {@code Set} field keeps set semantics, anything else gets
     * a list.
     */
    private Collection<Object> newCollection(Class<?> fieldType, int size) {
        if (Set.class.isAssignableFrom(fieldType)) {
            return new LinkedHashSet<>(Math.max(size, 1));
        }
        return new ArrayList<>(size);
    }

    /** What the file actually had, for an error an operator can act on. */
    private static String describeShape(Object rawValue) {
        if (rawValue == null) {
            return "nothing";
        }
        if (rawValue instanceof Map) {
            return "a section";
        }
        if (rawValue instanceof Collection) {
            return "a list";
        }
        return "a single value ('" + rawValue + "')";
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

            switch (metadata.getKind()) {
                case COLLECTION -> values.put(metadata.getKey(), encodeCollection(metadata, value));
                case MAP -> values.put(metadata.getKey(), encodeMap(metadata, value));
                default -> values.put(metadata.getKey(), FieldCodec.encode(value));
            }
        }
    }

    /**
     * Writes a collection out as a sequence. A null field becomes an empty one
     * rather than a null key, which no format would read back as a collection.
     */
    private List<Object> encodeCollection(FieldMetadata metadata, Object value) {
        List<Object> encoded = new ArrayList<>();
        if (value == null) {
            return encoded;
        }
        for (Object element : (Collection<?>) value) {
            encoded.add(encodeElement(metadata, element));
        }
        return encoded;
    }

    /** Writes a {@code Map<String, V>} out as a section, in the map's own order. */
    private Map<String, Object> encodeMap(FieldMetadata metadata, Object value) {
        Map<String, Object> encoded = new LinkedHashMap<>();
        if (value == null) {
            return encoded;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            encoded.put(String.valueOf(entry.getKey()), encodeElement(metadata, entry.getValue()));
        }
        return encoded;
    }

    /**
     * One element or value. Config-object elements are collected without
     * comments: a {@code @Comment} inside an element type would have to be
     * repeated per element, and no format carries a comment per sequence item
     * in a way the backends can place reliably.
     */
    private Object encodeElement(FieldMetadata metadata, Object element) {
        if (metadata.getElementChildren() == null) {
            return FieldCodec.encode(element);
        }
        if (element == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> encoded = new LinkedHashMap<>();
        collect(element, metadata.getElementChildren(), encoded, new LinkedHashMap<>(), "");
        return encoded;
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
