package net.loyalnetwork.coffeelib.api.exception;

/**
 * Root of every failure raised by CoffeeLib, across all of its concerns
 * (config, dependency injection, ...). Unchecked — these are operator- or
 * developer-facing problems, not something calling code is expected to
 * recover from inline.
 */
public class CoffeeLibException extends RuntimeException {

    public CoffeeLibException(String message) {
        super(message);
    }

    public CoffeeLibException(String message, Throwable cause) {
        super(message, cause);
    }
}
