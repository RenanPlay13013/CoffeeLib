package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** open() reads database.yml through whichever config manager - and so directory - the platform chose. */
class DatabaseManagerTest {

    @Test
    void openReadsTheFileThroughTheGivenConfigManagerOnlyWhenAsked() {
        AtomicInteger loads = new AtomicInteger();
        ConfigManager configs = new ConfigManager() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T load(Class<T> type) {
                loads.incrementAndGet();
                DatabaseConfig config = new DatabaseConfig();
                config.type = DatabaseType.POSTGRESQL;
                config.host = "127.0.0.1";
                config.port = 1;
                config.connectionTimeoutMillis = 1000;
                return (T) config;
            }

            @Override
            public void reload(Object config) {
            }

            @Override
            public void save(Object config) {
            }
        };
        DefaultDatabaseManager manager = new DefaultDatabaseManager("testmod", () -> configs,
                getClass().getClassLoader());
        assertEquals(0, loads.get(), "nothing is read until open()");

        DatabaseException failed = assertThrows(DatabaseException.class, manager::open);

        assertEquals(1, loads.get());
        assertTrue(failed.getMessage().contains("127.0.0.1:1"), failed.getMessage());
        assertTrue(failed.getMessage().contains("testmod's database.yml"), failed.getMessage());
    }
}
