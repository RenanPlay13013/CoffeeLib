package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the real entry point a plugin author would call —
 * {@link CoffeeLib#forPlugin} — against a mocked {@link JavaPlugin}, with
 * real reflection, real nested-object handling, and real file I/O via
 * {@link YamlConfigBackend}. No fakes below {@code JavaPlugin} itself.
 */
class CoffeeLibTest {

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

    @ConfigFile("server")
    public static final class ServerConfig {
        @Comment("Servidor principal")
        public String host = "localhost";

        public DatabaseConfig database = new DatabaseConfig();
    }

    @Test
    void loadsDeeplyNestedConfigThroughRealPaperEntryPoint(@TempDir Path tempDir) throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        ConfigManager manager = CoffeeLib.forPlugin(plugin);
        ServerConfig config = manager.load(ServerConfig.class);

        assertEquals("localhost", config.host);
        assertEquals("db.local", config.database.host);
        assertEquals(5432, config.database.port);
        assertEquals("root", config.database.credentials.username);

        Path file = tempDir.resolve("server.yml");
        assertTrue(Files.exists(file));

        String raw = Files.readString(file);
        assertTrue(raw.contains("# Servidor principal"));
        assertTrue(raw.contains("database:"));
        assertTrue(raw.contains("  # Host do banco de dados"));
        assertTrue(raw.contains("  host: db.local"));
        assertTrue(raw.contains("  credentials:"));
        assertTrue(raw.contains("    username: root"));
    }

    @Test
    void reloadPicksUpEditsMadeDirectlyToTheFile(@TempDir Path tempDir) throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        ConfigManager manager = CoffeeLib.forPlugin(plugin);
        ServerConfig config = manager.load(ServerConfig.class);
        DatabaseConfig originalDatabase = config.database;

        Path file = tempDir.resolve("server.yml");
        String edited = Files.readString(file).replace("host: db.local", "host: prod.example");
        Files.writeString(file, edited);

        manager.reload(config);

        assertEquals("prod.example", config.database.host);
        assertTrue(config.database == originalDatabase, "nested instance should be reused, not replaced");
    }
}
