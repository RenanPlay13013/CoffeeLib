package net.loyalnetwork.coffeelib.api.exception;

/**
 * Base type for every failure raised by CoffeeLib. Unchecked — a broken
 * config is an operator-facing problem, not something calling code is
 * expected to recover from inline.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
