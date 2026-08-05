package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.ConfigBackend;
import net.loyalnetwork.coffeelib.api.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.exception.ConfigSaveException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Hand-rolled YAML backend for Paper. Deliberately does not use Bukkit's
 * {@code YamlConfiguration#save}, which discards comments on write — that
 * limitation was the whole reason to build a config lib instead of using
 * what Bukkit already ships.
 * <p>
 * Format/parsing lives in {@link YamlFormat} — this class only wires it to
 * disk. The writer/reader pair only has to agree with itself, so it stays
 * intentionally narrow: indented {@code key: value} pairs and section
 * headers, {@code # comment} lines above whatever they annotate. Nesting
 * depth is unbounded, matching {@code core}'s recursive field scan.
 */
final class YamlConfigBackend implements ConfigBackend {

    @Override
    public String fileExtension() {
        return "yml";
    }

    @Override
    public Map<String, Object> read(Path file) {
        try {
            return YamlFormat.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ConfigLoadException("Could not read " + file, e);
        }
    }

    @Override
    public void write(Path file, Map<String, Object> values, Map<String, String> comments) {
        try {
            Files.writeString(file, YamlFormat.format(values, comments), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigSaveException("Could not write " + file, e);
        }
    }
}
