package net.loyalnetwork.coffeelib.api.database;

/**
 * Opens databases for one owner - a mod or plugin.
 * <p>
 * Every database opened here is closed when its owner shuts down. A pool per
 * owner, rather than one shared by everyone: owners usually point at
 * different databases, and one owner's slow queries cannot starve another's.
 */
public interface DatabaseManager {

    /**
     * Opens the database described by the owner's {@code database.yml},
     * created with defaults on first use - in which case the defaults are
     * unlikely to connect, and this fails with a {@link DatabaseException}
     * saying which file to edit.
     *
     * @throws DatabaseException when the database cannot be reached
     */
    Database open();

    /**
     * Opens the database {@code settings} describe - for settings an owner
     * keeps in its own config, or builds in code.
     *
     * @throws DatabaseException when the database cannot be reached
     */
    Database open(DatabaseSettings settings);
}
