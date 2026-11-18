package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseManager;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;

import java.nio.file.Path;
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
    private final Supplier<Path> configDirectory;

    /**
     * @param configs     the owner's config manager, asked for only when
     *                    {@link #open()} needs {@code database.yml}
     * @param ownerLoader     the owner's class loader (see {@code PooledDatabase#connect})
     * @param configDirectory the directory {@code database.yml} is in, which a
     *                        relative embedded-database file is resolved against
     */
    public DefaultDatabaseManager(String ownerId, Supplier<ConfigManager> configs, ClassLoader ownerLoader,
                                  Supplier<Path> configDirectory) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.configs = configs;
        this.ownerLoader = ownerLoader;
        this.configDirectory = configDirectory;
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
        PooledDatabase database = PooledDatabase.connect(ownerId, ownerLoader, resolveFile(settings));
        OpenDatabases.add(ownerId, database);
        return database;
    }

    /** An embedded database's relative file, made absolute against the owner's config directory. */
    DatabaseSettings resolveFile(DatabaseSettings settings) {
        if (!settings.type().isEmbedded() || settings.jdbcUrl() != null) {
            return settings;
        }
        Path file = Path.of(settings.file());
        if (file.isAbsolute() || configDirectory == null) {
            return settings;
        }
        return settings.withFile(configDirectory.get().resolve(file).toAbsolutePath().toString());
    }

    /** The open databases of every owner, for {@code /coffeelib} and diagnostics. */
    public static Map<String, Integer> openCounts() {
        return OpenDatabases.counts();
    }
}
