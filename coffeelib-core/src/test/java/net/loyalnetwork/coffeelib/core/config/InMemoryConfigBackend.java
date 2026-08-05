package net.loyalnetwork.coffeelib.core;

import net.loyalnetwork.coffeelib.api.ConfigBackend;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-only backend: values are kept in memory rather than actually
 * serialized, but {@code write} still touches the real path on disk so
 * {@code DefaultConfigManager}'s {@code Files.exists} check behaves exactly
 * as it would with a real YAML/TOML backend.
 */
final class InMemoryConfigBackend implements ConfigBackend {

    final Map<Path, Map<String, Object>> stored = new HashMap<>();
    final Map<Path, Map<String, String>> storedComments = new HashMap<>();

    @Override
    public String fileExtension() {
        return "mem";
    }

    @Override
    public Map<String, Object> read(Path file) {
        return new LinkedHashMap<>(stored.getOrDefault(file, Map.of()));
    }

    @Override
    public void write(Path file, Map<String, Object> values, Map<String, String> comments) {
        try {
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        stored.put(file, new LinkedHashMap<>(values));
        storedComments.put(file, new LinkedHashMap<>(comments));
    }
}
