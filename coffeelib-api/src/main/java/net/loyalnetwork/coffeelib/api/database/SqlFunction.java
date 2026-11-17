package net.loyalnetwork.coffeelib.api.database;

import java.sql.SQLException;

/** A function that may throw {@link SQLException}: a row mapper, or work done on a connection. */
@FunctionalInterface
public interface SqlFunction<T, R> {

    R apply(T value) throws SQLException;
}
