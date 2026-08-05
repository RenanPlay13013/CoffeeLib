package net.loyalnetwork.coffeelib.core;

import net.loyalnetwork.coffeelib.api.ConfigManager;
import net.loyalnetwork.coffeelib.api.annotation.Comment;
import net.loyalnetwork.coffeelib.api.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.annotation.Range;
import net.loyalnetwork.coffeelib.api.exception.ConfigException;
import net.loyalnetwork.coffeelib.api.exception.ConfigValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigManagerTest {

    @ConfigFile("server")
    public static final class ServerConfig {
        @Comment("Servidor principal")
        public String host = "localhost";

        @Range(min = 1, max = 65535)
        public int port = 3306;

        @OneOf({"easy", "normal", "hard"})
        public String difficulty = "normal";
    }

    @Test
    void loadOnFirstRunKeepsDefaultsAndWritesThem(@TempDir Path tempDir) {
        InMemoryConfigBackend backend = new InMemoryConfigBackend();
        ConfigManager manager = new DefaultConfigManager(tempDir, backend);

        ServerConfig config = manager.load(ServerConfig.class);

        assertEquals("localhost", config.host);
        assertEquals(3306, config.port);
        assertEquals("normal", config.difficulty);
        assertTrue(backend.stored.containsKey(tempDir.resolve("server.mem")));
        assertEquals("Servidor principal", backend.storedComments.get(tempDir.resolve("server.mem")).get("host"));
    }

    @Test
    void loadPicksUpValuesAlreadyOnDisk(@TempDir Path tempDir) {
        InMemoryConfigBackend backend = new InMemoryConfigBackend();
        Path path = tempDir.resolve("server.mem");
        backend.write(path, java.util.Map.of("host", "example.org", "port", 25565, "difficulty", "hard"), java.util.Map.of());

        ConfigManager manager = new DefaultConfigManager(tempDir, backend);
        ServerConfig config = manager.load(ServerConfig.class);

        assertEquals("example.org", config.host);
        assertEquals(25565, config.port);
        assertEquals("hard", config.difficulty);
    }

    @Test
    void rangeViolationThrows(@TempDir Path tempDir) {
        InMemoryConfigBackend backend = new InMemoryConfigBackend();
        Path path = tempDir.resolve("server.mem");
        backend.write(path, java.util.Map.of("host", "localhost", "port", 99999, "difficulty", "normal"), java.util.Map.of());

        ConfigManager manager = new DefaultConfigManager(tempDir, backend);
        ConfigValidationException exception = assertThrows(ConfigValidationException.class, () -> manager.load(ServerConfig.class));

        assertEquals("port", exception.getFieldName());
    }

    @Test
    void oneOfViolationSuggestsClosestMatch(@TempDir Path tempDir) {
        InMemoryConfigBackend backend = new InMemoryConfigBackend();
        Path path = tempDir.resolve("server.mem");
        backend.write(path, java.util.Map.of("host", "localhost", "port", 3306, "difficulty", "hrad"), java.util.Map.of());

        ConfigManager manager = new DefaultConfigManager(tempDir, backend);
        ConfigValidationException exception = assertThrows(ConfigValidationException.class, () -> manager.load(ServerConfig.class));

        assertEquals("hard", exception.getSuggestions().get(0));
    }

    @Test
    void reloadUpdatesExistingInstanceInPlace(@TempDir Path tempDir) {
        InMemoryConfigBackend backend = new InMemoryConfigBackend();
        ConfigManager manager = new DefaultConfigManager(tempDir, backend);

        ServerConfig config = manager.load(ServerConfig.class);
        Path path = tempDir.resolve("server.mem");
        backend.write(path, java.util.Map.of("host", "changed.example", "port", 1234, "difficulty", "easy"), java.util.Map.of());

        manager.reload(config);

        assertEquals("changed.example", config.host);
        assertEquals(1234, config.port);
        assertEquals("easy", config.difficulty);
    }

    public static final class CredentialsConfig {
        public String username = "root";
        public String password = "changeme";
    }

    public static final class DatabaseConfig {
        @Comment("Host do banco de dados")
        public String host = "db.local";

        @Range(min = 1, max = 65535)
        public int port = 5432;

        public CredentialsConfig credentials = new CredentialsConfig();
    }

    @ConfigFile("app")
    public static final class AppConfig {
        public String name = "coffee";
        public DatabaseConfig database = new DatabaseConfig();
    }

    @Test
    void loadHandlesArbitraryNestingDepth(@TempDir Path tempDir) {
        InMemoryConfigBackend backend = new InMemoryConfigBackend();
        ConfigManager manager = new DefaultConfigManager(tempDir, backend);

        AppConfig config = manager.load(AppConfig.class);

        assertEquals("coffee", config.name);
        assertEquals("db.local", config.database.host);
        assertEquals(5432, config.database.port);
        assertEquals("root", config.database.credentials.username);

        Path path = tempDir.resolve("app.mem");
        @SuppressWarnings("unchecked")
        Map<String, Object> databaseSection = (Map<String, Object>) backend.stored.get(path).get("database");
        @SuppressWarnings("unchecked")
        Map<String, Object> credentialsSection = (Map<String, Object>) databaseSection.get("credentials");
        assertEquals("root", credentialsSection.get("username"));
        assertEquals("Host do banco de dados", backend.storedComments.get(path).get("database.host"));
    }

    @Test
    void reloadUpdatesNestedInstancesInPlace(@TempDir Path tempDir) {
        InMemoryConfigBackend backend = new InMemoryConfigBackend();
        ConfigManager manager = new DefaultConfigManager(tempDir, backend);

        AppConfig config = manager.load(AppConfig.class);
        DatabaseConfig originalDatabase = config.database;

        Path path = tempDir.resolve("app.mem");
        backend.write(path, Map.of(
                "name", "coffee",
                "database", Map.of(
                        "host", "changed.example",
                        "port", 6543,
                        "credentials", Map.of("username", "admin", "password", "secret"))
        ), Map.of());

        manager.reload(config);

        assertEquals("changed.example", config.database.host);
        assertEquals(6543, config.database.port);
        assertEquals("admin", config.database.credentials.username);
        assertTrue(config.database == originalDatabase, "nested instance should be reused, not replaced");
    }

    public static final class SelfReferencing {
        public SelfReferencing self;
    }

    @ConfigFile("broken")
    public static final class CircularConfig {
        public SelfReferencing nested = new SelfReferencing();
    }

    @Test
    void circularNestingIsRejectedAtScanTime(@TempDir Path tempDir) {
        ConfigManager manager = new DefaultConfigManager(tempDir, new InMemoryConfigBackend());

        assertThrows(ConfigException.class, () -> manager.load(CircularConfig.class));
    }
}
