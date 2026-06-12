package net.loyalnetwork.coffeelib.config.node;

import net.loyalnetwork.coffeelib.config.serializer.ConfigSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public final class ConfigValue<T> {

    private final T defaultValue;

    private String path;
    private FileConfiguration configuration;
    private ConfigSerializer<T> serializer;

    private T cachedValue;

    private ConfigValue(T defaultValue) {
        this.defaultValue = defaultValue;
        this.cachedValue = defaultValue;
    }

    public static <T> ConfigValue<T> of(T defaultValue) {
        return new ConfigValue<>(defaultValue);
    }

    public T get() {
        return cachedValue;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public void set(T value) {
        if (configuration == null) {
            throw new IllegalStateException("ConfigValue is not bound to a configuration.");
        }
        Object serialized = serializer != null ? serializer.serialize(value) : value;
        configuration.set(path, serialized);
        cachedValue = value;
    }

    @SuppressWarnings("unchecked")
    public void reload() {
        if (configuration == null) {
            throw new IllegalStateException("ConfigValue is not bound to a configuration.");
        }
        if (serializer != null) {
            Object raw = configuration.get(path);
            if (raw instanceof ConfigurationSection section) {
                raw = section.getValues(false);
            }
            if (raw != null) {
                cachedValue = serializer.deserialize(raw);
            } else {
                cachedValue = defaultValue;
            }
        } else {
            cachedValue = (T) configuration.get(path, defaultValue);
        }
    }

    public boolean isBound() {
        return configuration != null;
    }

    public void bind(String path, FileConfiguration configuration) {
        this.path = path;
        this.configuration = configuration;
        reload();
    }

    public void bind(String path, FileConfiguration configuration, ConfigSerializer<T> serializer) {
        this.serializer = serializer;
        bind(path, configuration);
    }
}
