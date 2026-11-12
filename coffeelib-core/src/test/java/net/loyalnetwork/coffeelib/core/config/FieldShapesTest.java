package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.core.config.format.ConfigBackends;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every field shape a config class can declare, and what CoffeeLib does with
 * it. Written from a bug report against 3.1 whose point was that the answer
 * for several shapes was "writes {} over the operator's data and says nothing".
 */
class FieldShapesTest {

    public static final class Nested {
        public String name = "nested-works";
        public int value = 42;
    }

    @ConfigFile(value = "probe", format = ConfigFormat.YAML)
    public static final class ProbeConfig {
        public String text = "ok";
        public boolean flag = true;
        public int number = 1;
        public long big = 123L;
        public double fraction = 1.5;
        public byte tiny = 7;
        public char letter = 'x';
        public Byte boxedTiny = 8;
        public Character boxedLetter = 'y';
        public Nested nested = new Nested();
        public List<String> words = new ArrayList<>(List.of("a", "b"));
        public List<Nested> crates = new ArrayList<>(List.of(new Nested()));
        public Map<String, String> labels = new LinkedHashMap<>(Map.of("k", "v"));
    }

    private static DefaultConfigManager manager(Path directory) {
        return new DefaultConfigManager(directory, ConfigBackends.of(ConfigFormat.YAML));
    }

    @Test
    void everyShapeRoundTripsThroughTheFile(@TempDir Path dir) throws IOException {
        ProbeConfig written = manager(dir).load(ProbeConfig.class);
        String raw = Files.readString(dir.resolve("probe.yml"));

        // The shapes the report found written as "{}", each of which destroyed whatever an
        // operator had typed under that key on the next start.
        assertTrue(raw.contains("tiny: 7"), raw);
        assertTrue(raw.contains("letter: x"), raw);
        assertTrue(raw.contains("boxedTiny: 8"), raw);
        assertTrue(raw.contains("boxedLetter: y"), raw);
        assertTrue(raw.contains("words:"), raw);
        assertTrue(raw.contains("- a"), raw);
        assertTrue(raw.contains("crates:"), raw);
        assertTrue(raw.contains("name: nested-works"), raw);
        assertTrue(raw.contains("labels:"), raw);
        assertTrue(raw.contains("k: v"), raw);
        assertTrue(raw.contains("{}") == false, "nothing should be written as an empty mapping: " + raw);

        assertEquals(List.of("a", "b"), written.words);
    }

    @Test
    void handEditedValuesSurviveALoad(@TempDir Path dir) throws IOException {
        manager(dir).load(ProbeConfig.class);

        Path file = dir.resolve("probe.yml");
        String edited = Files.readString(file)
                .replace("- a\n", "- x\n")
                .replace("tiny: 7", "tiny: 9")
                .replace("letter: x", "letter: z")
                .replace("k: v", "k: edited");
        Files.writeString(file, edited);

        // The report's data-loss path: load() used to return the field initializers and
        // rewrite the file, throwing the operator's edits away.
        ProbeConfig reloaded = manager(dir).load(ProbeConfig.class);

        assertEquals((byte) 9, reloaded.tiny);
        assertEquals('z', reloaded.letter);
        assertEquals("x", reloaded.words.get(0));
        assertEquals("edited", reloaded.labels.get("k"));
        assertTrue(Files.readString(file).contains("- x"), "and the edit survives the rewrite");
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class ArrayConfig {
        public String[] values = {"a"};
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class RawListConfig {
        @SuppressWarnings("rawtypes")
        public List values = new ArrayList();
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class WildcardConfig {
        public List<? extends Number> values = new ArrayList<>();
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class NonStringKeyConfig {
        public Map<Integer, String> values = new LinkedHashMap<>();
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class JdkTypeConfig {
        public java.time.Duration timeout = java.time.Duration.ZERO;
    }

    public interface Strategy {
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class InterfaceConfig {
        public Strategy strategy = new Strategy() {
        };
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class JdkInterfaceConfig {
        public Runnable task = () -> {
        };
    }

    @ConfigFile(value = "bad", format = ConfigFormat.YAML)
    public static final class RangeOnListConfig {
        @net.loyalnetwork.coffeelib.api.config.annotation.Range(min = 1, max = 2)
        public List<Integer> values = new ArrayList<>();
    }

    @Test
    void shapesThatCannotBeRepresentedFailAtScanTimeWithAUsefulMessage(@TempDir Path dir) {
        // The report's point: better one clear message at startup than data written as {}
        // or a JPMS stack trace from deep inside the reflector.
        assertMessage(dir, ArrayConfig.class, "declare it as a List<String>");
        assertMessage(dir, RawListConfig.class, "must declare its type arguments");
        assertMessage(dir, WildcardConfig.class, "cannot resolve");
        assertMessage(dir, NonStringKeyConfig.class, "must be keyed by String");
        assertMessage(dir, JdkTypeConfig.class, "no codec for");
        assertMessage(dir, InterfaceConfig.class, "cannot be instantiated");
        // A JDK interface is caught by the JDK guard first, which is the better message anyway.
        assertMessage(dir, JdkInterfaceConfig.class, "no codec for");
        assertMessage(dir, RangeOnListConfig.class, "only apply to scalar fields");

        assertTrue(Files.notExists(dir.resolve("bad.yml")), "and nothing is written over the operator's file");
    }

    private static void assertMessage(Path dir, Class<?> type, String expected) {
        ConfigException e = assertThrows(ConfigException.class, () -> manager(dir).load(type),
                type.getSimpleName() + " should be rejected");
        assertTrue(e.getMessage().contains(expected),
                type.getSimpleName() + ": expected a message mentioning '" + expected + "' but got: "
                        + e.getMessage());
    }

    @Test
    void aSetKeepsSetSemantics(@TempDir Path dir) {
        SetConfig config = manager(dir).load(SetConfig.class);
        assertEquals(2, config.tags.size());
        assertTrue(config.tags instanceof java.util.Set);
    }

    @ConfigFile(value = "set", format = ConfigFormat.YAML)
    public static final class SetConfig {
        public java.util.Set<String> tags = new java.util.LinkedHashSet<>(List.of("a", "b"));
    }

    @Test
    void aListOfNestedObjectsKeepsItsElements(@TempDir Path dir) throws IOException {
        manager(dir).load(ProbeConfig.class);
        Path file = dir.resolve("probe.yml");

        Files.writeString(file, Files.readString(file).replace("""
                crates:
                - name: nested-works
                  value: 42""", """
                crates:
                - name: diamond
                  value: 1
                - name: iron
                  value: 2"""));

        ProbeConfig reloaded = manager(dir).load(ProbeConfig.class);

        assertEquals(2, reloaded.crates.size(), "a catalogue of objects is the shape the report needed");
        assertEquals("diamond", reloaded.crates.get(0).name);
        assertEquals(2, reloaded.crates.get(1).value);
    }
}
