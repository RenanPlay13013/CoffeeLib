package net.loyalnetwork.coffeelib.api.database;

/**
 * The databases CoffeeLib can connect to: three servers, and two embedded
 * ones that live in a file next to the owner's configs and need no server at
 * all.
 */
public enum DatabaseType {

    /** MySQL 5.7+ / 8.x, through the bundled MariaDB driver, which speaks MySQL's protocol. */
    MYSQL(3306),

    MARIADB(3306),

    POSTGRESQL(5432),

    /**
     * SQLite, in one file. SQLite lets one connection write at a time, so the
     * pool holds a single connection whatever the pool size says.
     */
    SQLITE(0),

    /** H2, embedded, in one file ({@code <file>.mv.db}). */
    H2(0);

    private final int defaultPort;

    DatabaseType(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /** Whether this database lives in a local file rather than on a server. */
    public boolean isEmbedded() {
        return this == SQLITE || this == H2;
    }
}
