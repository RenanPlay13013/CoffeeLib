package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code database.yml}: what {@code DatabaseManager#open()} connects with. */
@ConfigFile(value = "database", format = ConfigFormat.YAML)
public final class DatabaseConfig {

    @Comment("MYSQL, MARIADB or POSTGRESQL")
    public DatabaseType type = DatabaseType.MYSQL;

    public String host = "localhost";

    @Comment("0 = the type's usual port (3306 for MySQL/MariaDB, 5432 for PostgreSQL)")
    @Range(min = 0, max = 65535)
    public int port = 0;

    public String database = "minecraft";

    public String username = "minecraft";

    @Comment("Any value here may be ${NAME} to read the environment variable NAME instead")
    public String password = "";

    @Comment("Connections kept open, and threads running queries")
    @Range(min = 1, max = 100)
    public int poolSize = 10;

    @Comment("Milliseconds to wait for a connection before giving up")
    @Range(min = 250, max = 600000)
    public long connectionTimeoutMillis = 10000;

    @Comment("Extra JDBC driver properties, e.g. sslMode: require (MySQL/MariaDB) or ssl: \"true\" (PostgreSQL)")
    public Map<String, String> properties = new LinkedHashMap<>();

    @Comment("A full JDBC URL to use instead of host/port/database; leave empty to build one")
    public String jdbcUrl = "";

    /** These values, with {@code ${NAME}} references resolved from the environment. */
    DatabaseSettings toSettings() {
        DatabaseSettings.Builder builder = DatabaseSettings.builder(type)
                .host(Environment.resolve(host, "host"))
                .port(port)
                .database(Environment.resolve(database, "database"))
                .username(Environment.resolve(username, "username"))
                .password(Environment.resolve(password, "password"))
                .poolSize(poolSize)
                .connectionTimeoutMillis(connectionTimeoutMillis)
                .jdbcUrl(Environment.resolve(jdbcUrl, "jdbcUrl"));
        properties.forEach((name, value) -> builder.property(name, Environment.resolve(value, "properties." + name)));
        return builder.build();
    }
}
