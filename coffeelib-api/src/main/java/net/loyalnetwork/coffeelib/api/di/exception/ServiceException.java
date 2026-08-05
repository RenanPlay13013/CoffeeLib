package net.loyalnetwork.coffeelib.api.di.exception;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

/** Base type for every dependency-injection failure. */
public class ServiceException extends CoffeeLibException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
