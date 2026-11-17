package net.loyalnetwork.coffeelib.api.database;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Where and how to connect. Build one with {@link #builder(DatabaseType)}:
 * <pre>{@code
 * DatabaseSettings settings = DatabaseSettings.builder(DatabaseType.POSTGRESQL)
 *         .host("db.example.com").database("crates")
 *         .username("crates").password(System.getenv("CRATES_DB_PASSWORD"))
 *         .build();
 * }</pre>
 * Driver properties ({@link Builder#property}) are passed to the JDBC driver
 * as they are - {@code sslMode}, {@code connectTimeout} and so on, named as
 * that driver names them.
 */
public final class DatabaseSettings {

    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final long connectionTimeoutMillis;
    private final Map<String, String> properties;
    private final String jdbcUrl;

    private DatabaseSettings(Builder builder) {
        this.type = builder.type;
        this.host = builder.host;
        this.port = builder.port > 0 ? builder.port : builder.type.defaultPort();
        this.database = builder.database;
        this.username = builder.username;
        this.password = builder.password;
        this.poolSize = builder.poolSize;
        this.connectionTimeoutMillis = builder.connectionTimeoutMillis;
        this.properties = Map.copyOf(builder.properties);
        this.jdbcUrl = builder.jdbcUrl;
    }

    public static Builder builder(DatabaseType type) {
        return new Builder(type);
    }

    public DatabaseType type() {
        return type;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String database() {
        return database;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public int poolSize() {
        return poolSize;
    }

    public long connectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public Map<String, String> properties() {
        return properties;
    }

    /** The JDBC URL to use as is, instead of one built from host, port and database; {@code null} when none. */
    public String jdbcUrl() {
        return jdbcUrl;
    }

    /** Settings with the password left out, so they can be logged. */
    @Override
    public String toString() {
        return "DatabaseSettings[" + type + " " + (jdbcUrl != null ? jdbcUrl : host + ":" + port + "/" + database)
                + ", user=" + username + ", poolSize=" + poolSize + "]";
    }

    public static final class Builder {
        private final DatabaseType type;
        private String host = "localhost";
        private int port;
        private String database = "minecraft";
        private String username = "";
        private String password = "";
        private int poolSize = 10;
        private long connectionTimeoutMillis = 10_000;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private String jdbcUrl;

        private Builder(DatabaseType type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        /** The port; 0 (the default) means the type's usual one. */
        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("port " + port + " is not between 0 and 65535");
            }
            this.port = port;
            return this;
        }

        public Builder database(String database) {
            this.database = Objects.requireNonNull(database, "database");
            return this;
        }

        public Builder username(String username) {
            this.username = Objects.requireNonNull(username, "username");
            return this;
        }

        public Builder password(String password) {
            this.password = password == null ? "" : password;
            return this;
        }

        /** Connections in the pool, and threads running the helpers. 10 by default. */
        public Builder poolSize(int poolSize) {
            if (poolSize < 1) {
                throw new IllegalArgumentException("poolSize must be at least 1, not " + poolSize);
            }
            this.poolSize = poolSize;
            return this;
        }

        /** How long to wait for a connection - from the pool, or to the server when opening. 10 s by default. */
        public Builder connectionTimeoutMillis(long millis) {
            if (millis < 250) {
                throw new IllegalArgumentException("connectionTimeoutMillis must be at least 250, not " + millis);
            }
            this.connectionTimeoutMillis = millis;
            return this;
        }

        public Builder property(String name, String value) {
            properties.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * A full JDBC URL, used as is - host, port and database are then
         * ignored. It must match the type ({@code jdbc:mariadb:} or
         * {@code jdbc:mysql:} for MySQL/MariaDB, {@code jdbc:postgresql:}).
         */
        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl == null || jdbcUrl.isBlank() ? null : jdbcUrl;
            return this;
        }

        public DatabaseSettings build() {
            return new DatabaseSettings(this);
        }
    }
}
