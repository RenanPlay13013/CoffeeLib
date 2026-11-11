package net.loyalnetwork.coffeelib.core.config.format;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each managed format has to satisfy the same {@code ConfigBackend} contract —
 * nested sections, scalar lists and the values coming back out the way they
 * went in — so the round-trip is asserted per format rather than per backend
 * class. Comments are the one deliberate difference: JSON cannot carry them
 * and is allowed to drop them.
 */
class ConfigBackendsTest {

    private static Map<String, Object> sampleValues() {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("username", "root");

        Map<String, Object> database = new LinkedHashMap<>();
        database.put("host", "db.local");
        database.put("port", 5432);
        database.put("credentials", credentials);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("host", "localhost");
        values.put("online", true);
        values.put("database", database);
        values.put("tags", List.of("survival", "pvp"));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static void assertRoundTrips(ConfigFormat format, Path tempDir) throws IOException {
        ConfigBackend backend = ConfigBackends.of(format);
        Path file = tempDir.resolve("server." + backend.fileExtension());

        backend.write(file, sampleValues(), Map.of("host", "Main server", "database.host", "DB host"));
        assertTrue(Files.exists(file), format + " should have written " + file.getFileName());

        Map<String, Object> read = backend.read(file);
        assertEquals("localhost", read.get("host"));
        assertEquals(true, read.get("online"));
        assertEquals(List.of("survival", "pvp"), read.get("tags"));

        Map<String, Object> database = (Map<String, Object>) read.get("database");
        assertEquals("db.local", database.get("host"));
        assertEquals(5432, ((Number) database.get("port")).intValue());
        assertEquals("root", ((Map<String, Object>) database.get("credentials")).get("username"));
    }

    @Test
    void yamlRoundTripsNestedValues(@TempDir Path tempDir) throws IOException {
        assertRoundTrips(ConfigFormat.YAML, tempDir);
    }

    @Test
    void jsonRoundTripsNestedValues(@TempDir Path tempDir) throws IOException {
        assertRoundTrips(ConfigFormat.JSON, tempDir);
    }

    @Test
    void hoconRoundTripsNestedValues(@TempDir Path tempDir) throws IOException {
        assertRoundTrips(ConfigFormat.HOCON, tempDir);
    }

    @Test
    void hoconWritesCommentsAboveTheirKeys(@TempDir Path tempDir) throws IOException {
        ConfigBackend backend = ConfigBackends.of(ConfigFormat.HOCON);
        Path file = tempDir.resolve("server.conf");

        backend.write(file, sampleValues(), Map.of("host", "Main server", "database.host", "DB host"));

        String raw = Files.readString(file);
        assertTrue(raw.contains("# Main server"), "HOCON's writer emits node comments itself");
        assertTrue(raw.contains("# DB host"), "including on nested nodes");
    }

    @Test
    void jsonDropsCommentsInsteadOfFailing(@TempDir Path tempDir) throws IOException {
        ConfigBackend backend = ConfigBackends.of(ConfigFormat.JSON);
        Path file = tempDir.resolve("server.json");

        backend.write(file, sampleValues(), Map.of("host", "Main server"));

        // Best-effort comments, per the ConfigBackend contract: the format has no syntax
        // for them, so the write succeeds and the comment is simply not there.
        assertFalse(Files.readString(file).contains("Main server"));
        assertEquals("localhost", backend.read(file).get("host"));
    }

    @Test
    void readingAMissingFileIsTheCallersProblemNotAnEmptyMap(@TempDir Path tempDir) {
        // core only calls read() after checking the file exists; the formats are allowed
        // to treat an absent file as empty rather than throwing, so assert what they do.
        assertTrue(ConfigBackends.of(ConfigFormat.YAML).read(tempDir.resolve("nope.yml")).isEmpty());
    }

    @Test
    void nativeHasNoPlatformAgnosticBackend() {
        ConfigException e = assertThrows(ConfigException.class, () -> ConfigBackends.of(ConfigFormat.NATIVE));
        assertTrue(e.getMessage().contains("NATIVE"));
    }

    @Test
    void extensionsMatchTheFormatTheyBelongTo() {
        assertEquals("yml", ConfigBackends.of(ConfigFormat.YAML).fileExtension());
        assertEquals("json", ConfigBackends.of(ConfigFormat.JSON).fileExtension());
        assertEquals("conf", ConfigBackends.of(ConfigFormat.HOCON).fileExtension());

        // The api's own extension table has to agree with the backends', since a
        // managed file is named from ConfigFormat before any backend is built.
        assertEquals(ConfigFormat.YAML.fileExtension(), ConfigBackends.of(ConfigFormat.YAML).fileExtension());
        assertEquals(ConfigFormat.JSON.fileExtension(), ConfigBackends.of(ConfigFormat.JSON).fileExtension());
        assertEquals(ConfigFormat.HOCON.fileExtension(), ConfigBackends.of(ConfigFormat.HOCON).fileExtension());
    }
}
