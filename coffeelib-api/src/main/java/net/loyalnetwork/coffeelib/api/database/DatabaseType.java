package net.loyalnetwork.coffeelib.api.database;

/** The servers CoffeeLib can connect to, each with its default port. */
public enum DatabaseType {

    /** MySQL 5.7+ / 8.x, through the bundled MariaDB driver, which speaks MySQL's protocol. */
    MYSQL(3306),

    MARIADB(3306),

    POSTGRESQL(5432);

    private final int defaultPort;

    DatabaseType(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }
}
