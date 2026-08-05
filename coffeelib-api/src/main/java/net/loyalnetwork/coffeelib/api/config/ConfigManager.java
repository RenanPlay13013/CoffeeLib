package net.loyalnetwork.coffeelib.api;

import net.loyalnetwork.coffeelib.api.exception.ConfigException;

/**
 * Entry point a developer holds on to. Bound to a single owner's environment
 * (a plugin's data folder, a mod's config directory) at creation time by the
 * platform module — nothing here is global or static, so two owners in the
 * same JVM never share state through this type.
 */
public interface ConfigManager {

    /**
     * Loads (creating with defaults on first run) the config backed by
     * {@code type}. {@code type} must be annotated with
     * {@link net.loyalnetwork.coffeelib.api.annotation.ConfigFile} and expose
     * a public no-args constructor.
     *
     * @throws ConfigException if the file exists but fails to parse, or a
     *                          field fails validation
     */
    <T> T load(Class<T> type);

    /** Re-reads the file backing {@code config} and updates its fields in place. */
    void reload(Object config);

    /** Writes the current in-memory state of {@code config} back to its file. */
    void save(Object config);
}
