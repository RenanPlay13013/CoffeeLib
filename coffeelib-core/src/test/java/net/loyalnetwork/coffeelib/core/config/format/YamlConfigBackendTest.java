package net.loyalnetwork.coffeelib.core.config.format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlConfigBackendTest {

    @Test
    void writeThenReadRoundTripsScalarValues(@TempDir Path tempDir) throws IOException {
        YamlConfigBackend backend = new YamlConfigBackend();
        Path file = tempDir.resolve("server.yml");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("host", "localhost");
        values.put("port", 3306);
        values.put("motd", "Servidor: teste # oficial");
        values.put("online", true);

        backend.write(file, values, Map.of("host", "Servidor principal"));

        String raw = Files.readString(file);
        assertTrue(raw.contains("# Servidor principal"), "comment should survive the write");
        assertTrue(raw.contains("host: localhost"), "configurate should serialize the key/value pairs");

        Map<String, Object> read = backend.read(file);
        assertEquals("localhost", read.get("host"));
        assertEquals(3306, read.get("port"));
        assertEquals("Servidor: teste # oficial", read.get("motd"));
        assertEquals(true, read.get("online"));
    }

    @Test
    void writeThenReadRoundTripsNestedSections(@TempDir Path tempDir) throws IOException {
        YamlConfigBackend backend = new YamlConfigBackend();
        Path file = tempDir.resolve("app.yml");

        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("username", "root");
        credentials.put("password", "changeme");

        Map<String, Object> database = new LinkedHashMap<>();
        database.put("host", "db.local");
        database.put("port", 5432);
        database.put("credentials", credentials);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "coffee");
        values.put("database", database);

        backend.write(file, values, Map.of(
                "database.host", "Host do banco de dados",
                "database.credentials.username", "Usuário administrativo"
        ));

        String raw = Files.readString(file);
        assertTrue(raw.contains("  # Host do banco de dados"));
        assertTrue(raw.contains("    # Usuário administrativo"));

        Map<String, Object> read = backend.read(file);
        assertEquals("coffee", read.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> readDatabase = (Map<String, Object>) read.get("database");
        assertEquals("db.local", readDatabase.get("host"));
        assertEquals(5432, readDatabase.get("port"));

        @SuppressWarnings("unchecked")
        Map<String, Object> readCredentials = (Map<String, Object>) readDatabase.get("credentials");
        assertEquals("root", readCredentials.get("username"));
        assertEquals("changeme", readCredentials.get("password"));
    }

    /**
     * A kits.yml shape: the same field names under two sections. Each comment
     * must land above its own section's key - matching by name alone put both
     * {@code items} comments above {@code menu.items}, and {@code preview.title}'s
     * above {@code menu.title}.
     */
    @Test
    void sameKeyNamesInDifferentSectionsKeepTheirOwnComments(@TempDir Path tempDir) throws IOException {
        YamlConfigBackend backend = new YamlConfigBackend();
        Path file = tempDir.resolve("kits.yml");

        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("title", "Kits");
        menu.put("items", java.util.List.of("starter", "vip"));
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("title", "Preview");
        preview.put("items", java.util.List.of("sword"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("menu", menu);
        values.put("preview", preview);

        Map<String, String> comments = new LinkedHashMap<>();
        comments.put("menu.title", "Menu title");
        comments.put("menu.items", "Kits listed in the menu");
        comments.put("preview.title", "Preview title");
        comments.put("preview.items", "Items shown in the preview");
        backend.write(file, values, comments);

        java.util.List<String> lines = Files.readAllLines(file);
        assertEquals("  # Menu title", lines.get(lines.indexOf("  title: Kits") - 1));
        assertEquals("  # Kits listed in the menu", lines.get(indexOfItems(lines, 0) - 1));
        assertEquals("  # Preview title", lines.get(lines.indexOf("  title: Preview") - 1));
        assertEquals("  # Items shown in the preview", lines.get(indexOfItems(lines, 1) - 1));
        for (String comment : comments.values()) {
            assertEquals(1, lines.stream().filter(line -> line.equals("  # " + comment)).count(),
                    "'" + comment + "' written exactly once");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> readPreview = (Map<String, Object>) backend.read(file).get("preview");
        assertEquals("Preview", readPreview.get("title"));
    }

    /** The line of the n-th {@code items:} key (0-based), comments skipped. */
    private static int indexOfItems(java.util.List<String> lines, int occurrence) {
        int seen = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("  items:") && seen++ == occurrence) {
                return i;
            }
        }
        throw new AssertionError("no items key #" + occurrence + " in\n" + String.join("\n", lines));
    }
}