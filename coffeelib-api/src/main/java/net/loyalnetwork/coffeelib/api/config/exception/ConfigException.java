package net.loyalnetwork.coffeelib.api.config.exception;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

/**
 * Base type for every config-related failure. Unchecked — a broken config
 * is an operator-facing problem, not something calling code is expected to
 * recover from inline.
 */
public class ConfigException extends CoffeeLibException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
