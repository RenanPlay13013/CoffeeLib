package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Telling a 3.6/3.7 file (written by the relocated H2) from an ordinary one.
 * Converting one needs the relocated H2, which only exists in the built jar,
 * so that part is checked against a real file from the jar, not here.
 */
class H2FileUpgradeTest {

    @TempDir
    Path directory;

    @AfterEach
    void closeAll() {
        OpenDatabases.closeOwner("upgrademod");
    }

    @Test
    void anOrdinaryH2FileIsNotMistakenForAnOldOneAndOpensUntouched() throws IOException {
        DefaultDatabaseManager manager = new DefaultDatabaseManager("upgrademod", () -> null,
                getClass().getClassLoader(), () -> directory);
        DatabaseSettings settings = DatabaseSettings.builder(DatabaseType.H2).file("plain").build();
        try (Database db = manager.open(settings)) {
            db.update("CREATE TABLE t (id INT)").join();
            db.update("INSERT INTO t VALUES (1)").join();
        }
        Path file = directory.resolve("plain.mv.db");
        assertFalse(H2FileUpgrade.writtenByRelocatedH2(file));

        try (Database db = manager.open(settings)) {
            assertEquals(1, db.queryFirst("SELECT COUNT(*) FROM t", rs -> rs.getInt(1)).join().orElseThrow());
        }
        assertFalse(Files.exists(directory.resolve("plain.mv.db.coffeelib-old")), "nothing was converted");
    }

    @Test
    void theRelocatedClassNamesGiveAnOldFileAway() throws IOException {
        Path file = directory.resolve("old.mv.db");
        Files.write(file, bytesWith("...net.loyalnetwork.coffeelib.libs.h2.mvstore.db.RowDataType$Factory...", 10));

        assertTrue(H2FileUpgrade.writtenByRelocatedH2(file));
    }

    @Test
    void theMarkerIsFoundAcrossTheBoundaryBetweenTwoReads() throws IOException {
        byte[] marker = "net.loyalnetwork.coffeelib.libs.h2".getBytes(StandardCharsets.US_ASCII);
        byte[] content = new byte[(1 << 16) + 100];
        Arrays.fill(content, (byte) 'x');
        System.arraycopy(marker, 0, content, (1 << 16) - 10, marker.length); // straddles the 64 KiB buffer
        Path file = directory.resolve("straddle.mv.db");
        Files.write(file, content);

        assertTrue(H2FileUpgrade.writtenByRelocatedH2(file));
    }

    @Test
    void aFileWithoutTheMarkerIsNotOld() throws IOException {
        Path file = directory.resolve("other.mv.db");
        Files.write(file, bytesWith("org.h2.mvstore.db.RowDataType$Factory", 5000));

        assertFalse(H2FileUpgrade.writtenByRelocatedH2(file));
    }

    private static byte[] bytesWith(String text, int repeat) {
        return text.repeat(repeat).getBytes(StandardCharsets.US_ASCII);
    }
}
