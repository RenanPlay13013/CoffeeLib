package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQLite and H2 in a file next to the owner's configs: a small database with no server. */
class EmbeddedDatabaseTest {

    @TempDir
    Path configDirectory;

    @AfterEach
    void closeAll() {
        OpenDatabases.closeOwner("homesmod");
    }

    private DefaultDatabaseManager manager() {
        return new DefaultDatabaseManager("homesmod", () -> null, getClass().getClassLoader(), () -> configDirectory);
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void dataSurvivesAReopenLikeHomesAcrossARestart(DatabaseType type) {
        DatabaseSettings settings = DatabaseSettings.builder(type).file("data/homes").build();

        try (Database first = manager().open(settings)) {
            first.update("CREATE TABLE homes (player VARCHAR(36), name VARCHAR(32), x DOUBLE, y DOUBLE, z DOUBLE, "
                    + "PRIMARY KEY (player, name))").join();
            first.update("INSERT INTO homes VALUES (?, ?, ?, ?, ?)", "p1", "base", 10.5, 64.0, -3.25).join();
        }
        try (Database second = manager().open(settings)) {
            List<Double> home = second.queryFirst("SELECT x, y, z FROM homes WHERE player = ? AND name = ?",
                    rs -> List.of(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)), "p1", "base").join().orElseThrow();
            assertEquals(List.of(10.5, 64.0, -3.25), home);
        }

        Path expected = configDirectory.resolve("data").resolve(type == DatabaseType.H2 ? "homes.mv.db" : "homes");
        assertTrue(Files.exists(expected), "relative to the config directory, parents created: " + expected);
    }

    @Test
    void sqliteTakesConcurrentWritesOneAtATime() {
        try (Database db = manager().open(DatabaseSettings.builder(DatabaseType.SQLITE).file("busy").poolSize(8).build())) {
            db.update("CREATE TABLE back (player INTEGER PRIMARY KEY, x DOUBLE)").join();

            CompletableFuture<?>[] writes = IntStream.range(0, 50)
                    .mapToObj(i -> db.update("INSERT INTO back VALUES (?, ?)", i, i * 1.5))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(writes).join(); // no SQLITE_BUSY: one connection, one writer

            assertEquals(50, db.queryFirst("SELECT COUNT(*) FROM back", rs -> rs.getInt(1)).join().orElseThrow());
        }
    }

    @Test
    void anAbsoluteFileIsUsedAsIs(@TempDir Path elsewhere) {
        Path file = elsewhere.resolve("shared");
        try (Database db = manager().open(DatabaseSettings.builder(DatabaseType.H2).file(file.toString()).build())) {
            db.update("CREATE TABLE t (id INT)").join();
        }
        assertTrue(Files.exists(elsewhere.resolve("shared.mv.db")));
    }

    @Test
    void embeddedUrlsPointAtTheFile() {
        assertEquals("jdbc:sqlite:C:/srv/config/homes/data",
                JdbcUrls.url(DatabaseSettings.builder(DatabaseType.SQLITE).file("C:\\srv\\config\\homes\\data").build()));
        assertEquals("jdbc:h2:file:/srv/config/homes/data",
                JdbcUrls.url(DatabaseSettings.builder(DatabaseType.H2).file("/srv/config/homes/data").build()));
    }
}
