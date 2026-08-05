package net.loyalnetwork.coffeelib.paper;

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
    void writeThenReadRoundTripsValuesAndPreservesComments(@TempDir Path tempDir) throws IOException {
        YamlConfigBackend backend = new YamlConfigBackend();
        Path file = tempDir.resolve("server.yml");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("host", "localhost");
        values.put("port", 3306);
        values.put("motd", "Servidor: teste # oficial");
        values.put("online", true);

        Map<String, String> comments = Map.of("host", "Servidor principal");

        backend.write(file, values, comments);

        String raw = Files.readString(file);
        assertTrue(raw.contains("# Servidor principal"), "comment should survive the write");

        Map<String, Object> read = backend.read(file);
        assertEquals("localhost", read.get("host"));
        assertEquals(3306L, read.get("port"));
        assertEquals("Servidor: teste # oficial", read.get("motd"));
        assertEquals(true, read.get("online"));
    }

    @Test
    void writeThenReadRoundTripsNestedSectionsAndTheirComments(@TempDir Path tempDir) {
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

        Map<String, String> comments = Map.of(
                "database.host", "Host do banco de dados",
                "database.credentials.username", "Usuário administrativo"
        );

        backend.write(file, values, comments);

        Map<String, Object> read = backend.read(file);
        assertEquals("coffee", read.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> readDatabase = (Map<String, Object>) read.get("database");
        assertEquals("db.local", readDatabase.get("host"));
        assertEquals(5432L, readDatabase.get("port"));

        @SuppressWarnings("unchecked")
        Map<String, Object> readCredentials = (Map<String, Object>) readDatabase.get("credentials");
        assertEquals("root", readCredentials.get("username"));
        assertEquals("changeme", readCredentials.get("password"));
    }
}
