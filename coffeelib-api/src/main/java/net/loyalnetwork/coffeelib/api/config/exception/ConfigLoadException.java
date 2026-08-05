package net.loyalnetwork.coffeelib.api.config.exception;

/** Raised when a config file cannot be read or parsed by the backend. */
public class ConfigLoadException extends ConfigException {

    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
