package net.loyalnetwork.coffeelib.api.database;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

/**
 * A database operation failed: the pool could not connect, a statement failed,
 * or the database was already closed. A future returned by {@link Database}
 * completes exceptionally with one of these, its cause the underlying
 * {@link java.sql.SQLException} when there is one.
 */
public class DatabaseException extends CoffeeLibException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
