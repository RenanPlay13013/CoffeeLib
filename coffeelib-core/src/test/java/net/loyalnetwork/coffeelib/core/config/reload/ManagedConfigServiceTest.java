package net.loyalnetwork.coffeelib.core.config.reload;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The managed side of config handling: the half that exists so a Forge-like
 * platform's configs stop depending on the mod-loading lifecycle. The two
 * things worth proving are that {@code load} is finished when it returns (no
 * "fields arrive later"), and that reloading works both on demand and from
 * the watcher.
 */
class ManagedConfigServiceTest {

    public static final class DatabaseConfig {
        @Comment("DB host")
        public String host = "db.local";

        @Range(min = 1, max = 65535)
        public int port = 5432;
    }

    @ConfigFile(value = "server", format = ConfigFormat.YAML)
    public static final class YamlServerConfig {
        @Comment("Main server")
        public String host = "localhost";

        public DatabaseConfig database = new DatabaseConfig();
    }

    @ConfigFile(value = "client", format = ConfigFormat.JSON)
    public static final class JsonClientConfig {
        public String name = "coffee";
    }

    @ConfigFile("native")
    public static final class NativeConfig {
        public String host = "localhost";
    }

    @Test
    void loadIsFinishedWhenItReturns(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);

        YamlServerConfig config = service.load(YamlServerConfig.class);

        // The whole point of a managed format on Forge/NeoForge: no ModConfigEvent has
        // to fire first, so the file exists and the instance is populated right here.
        assertTrue(Files.exists(tempDir.resolve("server.yml")));
        assertEquals("localhost", config.host);
        assertEquals("db.local", config.database.host);
        assertEquals(List.of("server.yml"), service.managedFiles());
    }

    @Test
    void eachFormatGetsItsOwnFileAndTheyCoexist(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);

        service.load(YamlServerConfig.class);
        service.load(JsonClientConfig.class);

        assertTrue(Files.exists(tempDir.resolve("server.yml")));
        assertTrue(Files.exists(tempDir.resolve("client.json")));
        assertEquals(List.of("server.yml", "client.json"), service.managedFiles());
    }

    @Test
    void nativeIsRejectedUnlessTheCallerNamesAFallback(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);

        // Forge-like platforms: NATIVE belongs to the loader, not here.
        assertThrows(ConfigException.class, () -> service.load(NativeConfig.class));

        // Paper: NATIVE *is* YAML, so it resolves to a managed config instead.
        NativeConfig config = service.load(NativeConfig.class, ConfigFormat.YAML);
        assertEquals("localhost", config.host);
        assertTrue(Files.exists(tempDir.resolve("native.yml")));
    }

    @Test
    void manualReloadPicksUpAnEditAndNotifiesListeners(@TempDir Path tempDir) throws IOException {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);
        YamlServerConfig config = service.load(YamlServerConfig.class);

        AtomicInteger notified = new AtomicInteger();
        service.addReloadListener(reloaded -> {
            assertTrue(reloaded == config, "the listener gets the live instance, not a copy");
            notified.incrementAndGet();
        });

        edit(tempDir.resolve("server.yml"), "host: localhost", "host: prod.example");

        assertTrue(service.reload("server.yml"));
        assertEquals("prod.example", config.host);
        assertEquals(1, notified.get());

        // A name nothing manages is a "no" rather than an exception, so a command can
        // tell the operator they typed the wrong file.
        assertFalse(service.reload("unknown.yml"));
    }

    @Test
    void reloadAllCountsWhatSucceeded(@TempDir Path tempDir) throws IOException {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);
        service.load(YamlServerConfig.class);
        service.load(JsonClientConfig.class);

        assertEquals(2, service.reloadAll());

        // One unparseable file must not stop the other from reloading.
        Files.writeString(tempDir.resolve("client.json"), "{ not json");
        assertEquals(1, service.reloadAll());
    }

    @Test
    void anInvalidValueFailsTheReloadInsteadOfBeingClamped(@TempDir Path tempDir) throws IOException {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);
        YamlServerConfig config = service.load(YamlServerConfig.class);

        edit(tempDir.resolve("server.yml"), "port: 5432", "port: 99999");

        // Unlike Forge's own ValueSpec, which resets an out-of-range value to the
        // default with a log warning, a managed config surfaces it.
        assertThrows(ConfigValidationException.class, () -> service.reload("server.yml"));
        assertEquals(5432, config.database.port);
    }

    @Test
    void hotReloadStartsStopsAndAppliesChanges(@TempDir Path tempDir) throws Exception {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);
        YamlServerConfig config = service.load(YamlServerConfig.class);

        CountDownLatch reloaded = new CountDownLatch(1);
        service.addReloadListener(instance -> reloaded.countDown());

        assertFalse(service.isHotReloadRunning());
        assertTrue(service.startHotReload());
        assertTrue(service.isHotReloadRunning());
        assertFalse(service.startHotReload(), "starting twice is a no-op, not a second watcher");

        edit(tempDir.resolve("server.yml"), "host: localhost", "host: watched.example");

        assertTrue(reloaded.await(20, TimeUnit.SECONDS), "the watcher should have reloaded the edited file");
        assertEquals("watched.example", config.host);

        assertTrue(service.stopHotReload());
        assertFalse(service.isHotReloadRunning());
        assertFalse(service.stopHotReload(), "stopping twice is a no-op");
    }

    @Test
    void hotReloadNeedsSomethingToWatch(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);

        assertFalse(service.startHotReload(), "nothing loaded means nothing to watch");
        assertFalse(service.isHotReloadRunning());
    }

    @Test
    void saveWritesTheInMemoryStateBack(@TempDir Path tempDir) throws IOException {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);
        YamlServerConfig config = service.load(YamlServerConfig.class);

        config.host = "saved.example";
        service.save(config);

        assertTrue(Files.readString(tempDir.resolve("server.yml")).contains("host: saved.example"));
        assertTrue(service.owns(config));
        assertThrows(ConfigException.class, () -> service.reload(new YamlServerConfig()));
    }

    @ConfigFile(value = "mymod/server", format = ConfigFormat.YAML)
    public static final class NestedServerConfig {
        public String host = "nested.example";
    }

    @ConfigFile(value = "other/server", format = ConfigFormat.YAML)
    public static final class OtherNestedServerConfig {
        public String host = "other.example";
    }

    @Test
    void aConfigInASubdirectoryKeepsItsPathAsItsName(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);

        NestedServerConfig config = service.load(NestedServerConfig.class);

        assertTrue(Files.exists(tempDir.resolve("mymod").resolve("server.yml")));
        assertEquals(List.of("mymod/server.yml"), service.managedFiles());
        assertTrue(service.reload("mymod/server.yml"), "the name an operator sees is the one that works");
        assertEquals("nested.example", config.host);
    }

    @Test
    void theBareFileNameWorksWhileItIsUnambiguous(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);
        service.load(NestedServerConfig.class);

        assertTrue(service.reload("server.yml"), "one match, so the short name is enough");

        // A second config with the same file name makes it ambiguous, and guessing which
        // one an operator meant would be worse than saying so.
        service.load(OtherNestedServerConfig.class);
        ConfigException e = assertThrows(ConfigException.class, () -> service.reload("server.yml"));
        assertTrue(e.getMessage().contains("mymod/server.yml"));
        assertTrue(e.getMessage().contains("other/server.yml"));

        assertTrue(service.reload("other/server.yml"), "the full name still resolves");
    }

    @Test
    void loadingTwoConfigsOntoTheSameFileIsRejected(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir);
        service.load(YamlServerConfig.class);

        // Both would write the file, each overwriting the other's values.
        ConfigException e = assertThrows(ConfigException.class, () -> service.load(YamlServerConfig.class));
        assertTrue(e.getMessage().contains("already loaded"));
        assertEquals(List.of("server.yml"), service.managedFiles());
    }

    @Test
    void anEscapingNameIsRejectedRatherThanWritingOutsideTheBase(@TempDir Path tempDir) {
        ManagedConfigService service = new ManagedConfigService("testmod", tempDir.resolve("config"));

        assertThrows(ConfigException.class, () -> service.load(EscapingConfig.class));
        assertTrue(Files.notExists(tempDir.resolve("outside")), "nothing should have been written");
    }

    @ConfigFile(value = "../outside/server", format = ConfigFormat.YAML)
    public static final class EscapingConfig {
        public String host = "localhost";
    }

    private static void edit(Path file, String from, String to) throws IOException {
        Files.writeString(file, Files.readString(file).replace(from, to));
    }
}
