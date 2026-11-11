package net.loyalnetwork.coffeelib.core.config.format;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;

/**
 * Resolves a {@link ConfigFormat} to the backend implementing it.
 * <p>
 * This is where the api's format-as-an-enum meets {@code core}'s concrete
 * parsers. {@link ConfigFormat#NATIVE} has no entry on purpose: it means
 * "whatever the platform does", which only a platform module can answer — the
 * Forge-like modules hand NATIVE to the loader's own config system and never
 * reach this class.
 */
public final class ConfigBackends {

    private ConfigBackends() {
    }

    /**
     * @throws ConfigException for {@link ConfigFormat#NATIVE}, which no
     *         platform-agnostic backend can implement
     */
    public static ConfigBackend of(ConfigFormat format) {
        return switch (format) {
            case YAML -> new YamlConfigBackend();
            case JSON -> new JsonConfigBackend();
            case HOCON -> new HoconConfigBackend();
            case NATIVE -> throw new ConfigException(
                    "ConfigFormat.NATIVE has no platform-agnostic backend - the platform module handles it");
        };
    }
}
