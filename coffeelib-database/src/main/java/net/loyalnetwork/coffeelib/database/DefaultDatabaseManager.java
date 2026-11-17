package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseManager;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One owner's {@link DatabaseManager}. Platforms build it with the owner's
 * config manager (for {@code database.yml}) and class loader, and close the
 * owner's databases through {@link OpenDatabases} when the owner shuts down.
 */
public final class DefaultDatabaseManager implements DatabaseManager {

    private final String ownerId;
    private final Supplier<ConfigManager> configs;
    private final ClassLoader ownerLoader;

    /**
     * @param configs     the owner's config manager, asked for only when
     *                    {@link #open()} needs {@code database.yml}
     * @param ownerLoader the owner's class loader (see {@code PooledDatabase#connect})
     */
    public DefaultDatabaseManager(String ownerId, Supplier<ConfigManager> configs, ClassLoader ownerLoader) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.configs = configs;
        this.ownerLoader = ownerLoader;
    }

    @Override
    public Database open() {
        DatabaseConfig config = configs.get().load(DatabaseConfig.class);
        DatabaseSettings settings = config.toSettings();
        try {
            return open(settings);
        } catch (DatabaseException e) {
            throw new DatabaseException(e.getMessage() + " - check " + ownerId + "'s database.yml", e);
        }
    }

    @Override
    public Database open(DatabaseSettings settings) {
        Objects.requireNonNull(settings, "settings");
        PooledDatabase database = PooledDatabase.connect(ownerId, ownerLoader, settings);
        OpenDatabases.add(ownerId, database);
        return database;
    }

    /** The open databases of every owner, for {@code /coffeelib} and diagnostics. */
    public static Map<String, Integer> openCounts() {
        return OpenDatabases.counts();
    }
}
