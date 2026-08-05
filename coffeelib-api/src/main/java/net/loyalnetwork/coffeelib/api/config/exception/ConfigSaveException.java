package net.loyalnetwork.coffeelib.api.config.exception;

/** Raised when a config file cannot be written by the backend. */
public class ConfigSaveException extends ConfigException {

    public ConfigSaveException(String message) {
        super(message);
    }

    public ConfigSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
