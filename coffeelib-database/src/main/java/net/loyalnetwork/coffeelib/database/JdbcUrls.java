package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;

/** The JDBC URL and driver for some settings. */
final class JdbcUrls {

    private JdbcUrls() {
    }

    static String url(DatabaseSettings settings) {
        String url = settings.jdbcUrl();
        if (url != null) {
            if (!url.startsWith(prefixFor(settings.type()))
                    && !(settings.type() != DatabaseType.POSTGRESQL && url.startsWith("jdbc:mysql:"))) {
                throw new DatabaseException("jdbcUrl '" + url + "' is not a " + settings.type() + " URL; it should start "
                        + "with " + prefixFor(settings.type()));
            }
            // The MariaDB driver takes jdbc:mysql: only when told to.
            return url.startsWith("jdbc:mysql:") ? withParameter(url, "permitMysqlScheme=true") : url;
        }
        String host = settings.host().contains(":") && !settings.host().startsWith("[")
                ? "[" + settings.host() + "]" // an IPv6 literal
                : settings.host();
        return prefixFor(settings.type()) + "//" + host + ":" + settings.port() + "/" + settings.database();
    }

    private static String prefixFor(DatabaseType type) {
        return type == DatabaseType.POSTGRESQL ? "jdbc:postgresql:" : "jdbc:mariadb:";
    }

    private static String withParameter(String url, String flag) {
        return url + (url.contains("?") ? "&" : "?") + flag;
    }

    /**
     * The driver class for {@code type}. A class literal, not a string, so the
     * name is rewritten along with the class when the bundled drivers are
     * relocated.
     */
    static String driverClass(DatabaseType type) {
        return type == DatabaseType.POSTGRESQL
                ? org.postgresql.Driver.class.getName()
                : org.mariadb.jdbc.Driver.class.getName();
    }
}
