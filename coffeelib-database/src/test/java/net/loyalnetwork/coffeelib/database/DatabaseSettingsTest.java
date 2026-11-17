package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** URLs, drivers, defaults and ${ENV} references. */
class DatabaseSettingsTest {

    @Test
    void urlsAreBuiltPerTypeWithTheirUsualPorts() {
        assertEquals("jdbc:mariadb://localhost:3306/minecraft",
                JdbcUrls.url(DatabaseSettings.builder(DatabaseType.MYSQL).build()));
        assertEquals("jdbc:mariadb://db:3307/crates",
                JdbcUrls.url(DatabaseSettings.builder(DatabaseType.MARIADB).host("db").port(3307).database("crates").build()));
        assertEquals("jdbc:postgresql://localhost:5432/minecraft",
                JdbcUrls.url(DatabaseSettings.builder(DatabaseType.POSTGRESQL).build()));
        assertEquals("jdbc:postgresql://[::1]:5432/minecraft",
                JdbcUrls.url(DatabaseSettings.builder(DatabaseType.POSTGRESQL).host("::1").build()));
    }

    @Test
    void aGivenUrlIsUsedAsIsButMustMatchTheType() {
        assertEquals("jdbc:postgresql://a/b?ssl=true", JdbcUrls.url(DatabaseSettings.builder(DatabaseType.POSTGRESQL)
                .jdbcUrl("jdbc:postgresql://a/b?ssl=true").build()));
        assertEquals("jdbc:mysql://a/b?permitMysqlScheme=true", JdbcUrls.url(DatabaseSettings.builder(DatabaseType.MYSQL)
                .jdbcUrl("jdbc:mysql://a/b").build()), "the MariaDB driver takes jdbc:mysql: when told to");
        assertThrows(DatabaseException.class, () -> JdbcUrls.url(DatabaseSettings.builder(DatabaseType.POSTGRESQL)
                .jdbcUrl("jdbc:mysql://a/b").build()));
    }

    @Test
    void eachTypeGetsItsDriver() {
        assertTrue(JdbcUrls.driverClass(DatabaseType.MYSQL).endsWith("mariadb.jdbc.Driver"));
        assertTrue(JdbcUrls.driverClass(DatabaseType.POSTGRESQL).endsWith("postgresql.Driver"));
    }

    @Test
    void mysqlWithoutTlsSettingsMayFetchTheServersKey() {
        assertTrue(PooledDatabase.needsPublicKeyRetrieval(DatabaseSettings.builder(DatabaseType.MYSQL).build()));
        assertFalse(PooledDatabase.needsPublicKeyRetrieval(DatabaseSettings.builder(DatabaseType.MYSQL)
                .property("sslMode", "verify-full").build()), "an operator who chose TLS keeps it");
        assertFalse(PooledDatabase.needsPublicKeyRetrieval(DatabaseSettings.builder(DatabaseType.MYSQL)
                .jdbcUrl("jdbc:mariadb://a/b?allowPublicKeyRetrieval=false").build()));
        assertFalse(PooledDatabase.needsPublicKeyRetrieval(DatabaseSettings.builder(DatabaseType.MARIADB).build()),
                "MariaDB servers do not use caching_sha2_password");
    }

    @Test
    void thePasswordIsNotInToString() {
        String text = DatabaseSettings.builder(DatabaseType.MYSQL).username("crates").password("hunter2").build().toString();
        assertFalse(text.contains("hunter2"), text);
        assertTrue(text.contains("crates"));
    }

    @Test
    void environmentReferencesAreResolvedAndAMissingOneIsNamed() {
        Map<String, String> env = Map.of("DB_PASSWORD", "s3cr$t", "DB_HOST", "db.internal");

        assertEquals("s3cr$t", Environment.resolve("${DB_PASSWORD}", "password", env::get));
        assertEquals("jdbc:postgresql://db.internal/x", Environment.resolve("jdbc:postgresql://${DB_HOST}/x", "jdbcUrl", env::get));
        assertEquals("plain", Environment.resolve("plain", "password", env::get));
        DatabaseException missing = assertThrows(DatabaseException.class,
                () -> Environment.resolve("${NOPE}", "password", env::get));
        assertTrue(missing.getMessage().contains("NOPE") && missing.getMessage().contains("password"), missing.getMessage());
    }

    @Test
    void theConfigFileBecomesSettings() {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseType.POSTGRESQL;
        config.database = "crates";
        config.poolSize = 4;
        config.properties.put("ssl", "true");

        DatabaseSettings settings = config.toSettings();

        assertEquals(5432, settings.port(), "port 0 means the type's own");
        assertEquals(4, settings.poolSize());
        assertEquals(Map.of("ssl", "true"), settings.properties());
        assertEquals(null, settings.jdbcUrl(), "an empty URL means build one");
    }

    @Test
    void invalidValuesAreRefusedWhenBuilt() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.builder(DatabaseType.MYSQL).poolSize(0));
        assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.builder(DatabaseType.MYSQL).port(70000));
    }

    @Test
    void anUnreachableServerFailsWhenOpenedSayingWhere() {
        DatabaseSettings settings = DatabaseSettings.builder(DatabaseType.POSTGRESQL)
                .host("127.0.0.1").port(1).connectionTimeoutMillis(1000).build();

        DatabaseException failed = assertThrows(DatabaseException.class,
                () -> PooledDatabase.connect("testmod", getClass().getClassLoader(), settings));
        assertTrue(failed.getMessage().contains("127.0.0.1:1"), failed.getMessage());
    }
}
