package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;

/** The JDBC URL and driver for some settings. */
final class JdbcUrls {

    /** {@code org.h2.Driver}, spelled so the jar's relocation of org.h2 leaves it alone. */
    static final String H2_DRIVER = String.join(".", "org", "h2", "Driver");

    private JdbcUrls() {
    }

    static String url(DatabaseSettings settings) {
        String url = settings.jdbcUrl();
        if (url == null && settings.type().isEmbedded()) {
            // The file as the manager resolved it; forward slashes work on every OS for both.
            String file = settings.file().replace('\\', '/');
            return settings.type() == DatabaseType.SQLITE ? "jdbc:sqlite:" + file : "jdbc:h2:file:" + file;
        }
        if (url != null) {
            if (!url.startsWith(prefixFor(settings.type()))
                    && !(isMysqlFamily(settings.type()) && url.startsWith("jdbc:mysql:"))) {
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
        return switch (type) {
            case POSTGRESQL -> "jdbc:postgresql:";
            case SQLITE -> "jdbc:sqlite:";
            case H2 -> "jdbc:h2:";
            case MYSQL, MARIADB -> "jdbc:mariadb:";
        };
    }

    private static boolean isMysqlFamily(DatabaseType type) {
        return type == DatabaseType.MYSQL || type == DatabaseType.MARIADB;
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
        return switch (type) {
            case POSTGRESQL -> org.postgresql.Driver.class.getName();
            // The published H2, not the relocated copy in this jar (see H2FileUpgrade); built
            // from parts so the relocation does not rewrite it.
            case H2 -> H2_DRIVER;
            // Not relocated into CoffeeLib's classes (SQLite's native code is bound to this
            // package name, so it cannot be): the server's copy on Paper, a nested jar on
            // Forge and NeoForge. Named, not linked, so nothing fails until it is used.
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL, MARIADB -> org.mariadb.jdbc.Driver.class.getName();
        };
    }
}
