package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts H2 files written by CoffeeLib 3.6 and 3.7 into ordinary H2 files.
 * <p>
 * Those versions ran a relocated H2, and H2 stores its own class names in the
 * file, so their files name {@code net.loyalnetwork.coffeelib.libs.h2...}
 * types: the published H2 - and every database tool - reports them as
 * corrupted. CoffeeLib now uses the published H2, and keeps the relocated copy
 * only for this: it exports such a file as SQL, the file is set aside as
 * {@code <file>.mv.db.coffeelib-old}, and the SQL is imported into a new file
 * by the published H2. Once, on the first open; any other file is left alone.
 * <p>
 * In this class, {@code org.h2} names the relocated copy - the jar's relocation
 * rewrites them - while the published H2 is reached by name only.
 */
final class H2FileUpgrade {

    private static final Logger LOGGER = Logger.getLogger(H2FileUpgrade.class.getName());

    /** What the relocated H2 writes into a file, and the published one never does. */
    private static final byte[] RELOCATED_MARKER = "net.loyalnetwork.coffeelib.libs.h2".getBytes(StandardCharsets.US_ASCII);

    private H2FileUpgrade() {
    }

    /**
     * Converts the H2 database at {@code file} (without {@code .mv.db}) when
     * it was written by the relocated H2. Does nothing for a new file, an
     * ordinary one, or one that fails for any other reason - opening it then
     * reports that reason as usual.
     */
    static void upgradeIfNeeded(String ownerId, String file, ClassLoader ownerLoader) {
        Path data = Path.of(file + ".mv.db");
        if (!Files.exists(data)) {
            return;
        }
        // Decided from the file's bytes, before the published H2 touches it: a failed open
        // leaves the file locked inside this process, so nothing could convert it after.
        if (!writtenByRelocatedH2(data)) {
            return;
        }
        Driver published = publishedDriver(ownerId, ownerLoader);
        String url = "jdbc:h2:file:" + file.replace('\\', '/');

        LOGGER.info("[CoffeeLib] [" + ownerId + "] " + data + " was written by CoffeeLib 3.6/3.7's private H2, "
                + "which other tools cannot read; converting it to a standard H2 file");
        Path script = Path.of(file + ".coffeelib-upgrade.sql");
        Path old = Path.of(file + ".mv.db.coffeelib-old");
        try {
            exportWithRelocatedH2(url, script);
            Files.move(data, old, StandardCopyOption.REPLACE_EXISTING);
            try {
                importWithPublishedH2(published, url, script);
            } catch (SQLException | RuntimeException e) {
                // Put things back as they were: the old file is the only good copy.
                Files.deleteIfExists(data);
                Files.move(old, data, StandardCopyOption.REPLACE_EXISTING);
                throw e;
            }
            Files.deleteIfExists(script);
            LOGGER.info("[CoffeeLib] [" + ownerId + "] Converted " + data + "; the original is kept as " + old
                    + " and can be deleted once everything works");
        } catch (SQLException | IOException e) {
            throw new DatabaseException("[" + ownerId + "] Could not convert " + data + " to a standard H2 file: "
                    + e.getMessage() + ". It is unchanged, and still opens with CoffeeLib 3.7", e);
        }
    }

    private static void exportWithRelocatedH2(String url, Path script) throws SQLException {
        try (Connection legacy = new org.h2.Driver().connect(url, credentials());
             Statement statement = legacy.createStatement()) {
            statement.execute("SCRIPT TO '" + sqlPath(script) + "' CHARSET 'UTF-8'");
        }
    }

    private static void importWithPublishedH2(Driver published, String url, Path script) throws SQLException {
        try (Connection fresh = published.connect(url, credentials());
             Statement statement = fresh.createStatement()) {
            statement.execute("RUNSCRIPT FROM '" + sqlPath(script) + "' CHARSET 'UTF-8'");
        }
    }

    /** The embedded databases' user: CoffeeLib sets none, which H2 takes as the empty name and password. */
    private static Properties credentials() {
        Properties properties = new Properties();
        properties.setProperty("user", "");
        properties.setProperty("password", "");
        return properties;
    }

    private static String sqlPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
    }

    private static Driver publishedDriver(String ownerId, ClassLoader ownerLoader) {
        for (ClassLoader loader : new ClassLoader[]{ownerLoader, H2FileUpgrade.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                return (Driver) Class.forName(JdbcUrls.H2_DRIVER, true, loader).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                // try the next loader
            }
        }
        throw new DatabaseException("[" + ownerId + "] H2's driver (" + JdbcUrls.H2_DRIVER + ") is not on the "
                + "classpath. CoffeeLib's Forge and NeoForge jars carry it; on Paper it is a library of the plugin");
    }

    /** Whether {@code data} holds the relocated H2's class names - read through once, never into memory whole. */
    static boolean writtenByRelocatedH2(Path data) {
        try (InputStream input = Files.newInputStream(data)) {
            byte[] buffer = new byte[1 << 16];
            int carried = 0;
            int read;
            while ((read = input.read(buffer, carried, buffer.length - carried)) > 0) {
                int length = carried + read;
                if (indexOf(buffer, length) >= 0) {
                    return true;
                }
                // Keep the tail, in case the marker straddles two reads.
                carried = Math.min(RELOCATED_MARKER.length - 1, length);
                System.arraycopy(buffer, length - carried, buffer, 0, carried);
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[CoffeeLib] Could not read " + data, e);
            return false;
        }
    }

    private static int indexOf(byte[] buffer, int length) {
        outer:
        for (int i = 0; i + RELOCATED_MARKER.length <= length; i++) {
            for (int j = 0; j < RELOCATED_MARKER.length; j++) {
                if (buffer[i + j] != RELOCATED_MARKER[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
