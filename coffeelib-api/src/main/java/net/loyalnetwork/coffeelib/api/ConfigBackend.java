package net.loyalnetwork.coffeelib.api;

import net.loyalnetwork.coffeelib.api.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.exception.ConfigSaveException;

import java.nio.file.Path;
import java.util.Map;

/**
 * The seam between {@code core} and a platform module. A backend only knows
 * how to move a flat/nested key-value structure in and out of a file in its
 * native format (YAML, TOML, ...) — it has no notion of the annotated Java
 * class {@code core} is populating. {@code core} never depends on a
 * concrete format; each platform module provides exactly one backend.
 * <p>
 * Nested sections are plain nested {@code Map<String, Object>} values.
 * Comment keys use the same dotted path as the value they annotate
 * (e.g. {@code "server.port"}). A backend whose format cannot represent
 * comments (e.g. vanilla Bukkit YAML) is allowed to silently drop them
 * rather than fail — comment support is best-effort, not a contract every
 * backend must honor.
 */
public interface ConfigBackend {

    /** File extension this backend reads/writes, without the leading dot (e.g. {@code "yml"}). */
    String fileExtension();

    /**
     * Reads {@code file} into a key-value structure. Callers are expected to
     * have already checked the file exists.
     */
    Map<String, Object> read(Path file) throws ConfigLoadException;

    /**
     * Writes {@code values} to {@code file}, attaching {@code comments}
     * where the format supports it.
     */
    void write(Path file, Map<String, Object> values, Map<String, String> comments) throws ConfigSaveException;
}
