package net.loyalnetwork.coffeelib.neoforge;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link NeoForgeConfigManager} on both of its paths.
 * <p>
 * The native path runs against a mocked {@link ModContainer}/{@link IEventBus}:
 * spec construction (scanning the class, building the {@link ModConfigSpec}
 * tree via push/pop, wiring {@code @Range}/{@code @OneOf}) runs for real —
 * that part needs no running game, it's pure NightConfig underneath. What a
 * mock genuinely can't give us is NeoForge's own file-loading lifecycle
 * ({@code ModConfigSpec.acceptConfig} reaching into the real
 * {@code ConfigTracker}/{@code FMLPaths}), so it stops at "the listener
 * NeoForge will eventually drive is registered for the right event types".
 * <p>
 * The managed path needs none of that, which is the property worth testing:
 * a config in a CoffeeLib-managed format is read from disk during
 * {@code load}, so those tests assert against real files in a temp directory
 * with no NeoForge lifecycle involved at all.
 */
class CoffeeLibTest {

    public static final class CredentialsConfig {
        public String username = "root";
        public String password = "changeme";
    }

    public static final class DatabaseConfig {
        @Comment("Host do banco de dados")
        public String host = "db.local";

        @Range(min = 1, max = 65535)
        public int port = 5432;

        public CredentialsConfig credentials = new CredentialsConfig();
    }

    @ConfigFile("server")
    public static final class ServerConfig {
        @Comment("Servidor principal")
        public String host = "localhost";

        @OneOf({"easy", "normal", "hard"})
        public String difficulty = "normal";

        public DatabaseConfig database = new DatabaseConfig();
    }

    @ConfigFile(value = "managed", format = ConfigFormat.HOCON)
    public static final class ManagedServerConfig {
        @Comment("Servidor principal")
        public String host = "localhost";

        public DatabaseConfig database = new DatabaseConfig();
    }

    @ConfigFile(value = "managed-yaml", format = ConfigFormat.YAML)
    public static final class ManagedYamlConfig {
        public String name = "coffee";
    }

    private static NeoForgeConfigManager manager(ModContainer container, IEventBus eventBus, Path configDirectory) {
        return new NeoForgeConfigManager("examplemod", container, eventBus, () -> configDirectory);
    }

    @Test
    void nativeLoadRegistersASpecAndKeepsDefaultsUntilNeoForgeLoadsTheFile(@TempDir Path tempDir) {
        ModContainer container = mock(ModContainer.class);
        IEventBus eventBus = mock(IEventBus.class);

        ServerConfig config = manager(container, eventBus, tempDir).load(ServerConfig.class);

        // Nothing has told NeoForge to actually load the file yet — fields still hold class
        // defaults. Proves scanFields()/defineValue() walked every field (including two levels
        // of nesting and the @OneOf/@Range branches) without throwing.
        assertEquals("localhost", config.host);
        assertEquals("normal", config.difficulty);
        assertEquals("db.local", config.database.host);
        assertEquals(5432, config.database.port);
        assertEquals("root", config.database.credentials.username);
        assertEquals("changeme", config.database.credentials.password);

        verify(container).registerConfig(eq(ModConfig.Type.COMMON), any(ModConfigSpec.class), eq("server.toml"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theNativePathRegistersListenersForBothLoadingAndReloadingEvents(@TempDir Path tempDir) {
        ModContainer container = mock(ModContainer.class);
        IEventBus eventBus = mock(IEventBus.class);

        manager(container, eventBus, tempDir).load(ServerConfig.class);

        ArgumentCaptor<Class> classCaptor = ArgumentCaptor.forClass(Class.class);
        verify(eventBus, atLeastOnce()).addListener(classCaptor.capture(), any(Consumer.class));

        List<Class> registeredTypes = classCaptor.getAllValues();
        assertTrue(registeredTypes.contains(ModConfigEvent.Loading.class), "should listen for ModConfigEvent.Loading");
        assertTrue(registeredTypes.contains(ModConfigEvent.Reloading.class), "should listen for ModConfigEvent.Reloading");
    }

    @Test
    void aManagedConfigIsPopulatedDuringLoadWithNoModLoadingInvolved(@TempDir Path tempDir) {
        ModContainer container = mock(ModContainer.class);
        IEventBus eventBus = mock(IEventBus.class);

        ManagedServerConfig config = manager(container, eventBus, tempDir).load(ManagedServerConfig.class);

        // The file exists and the instance is populated right now: no ModConfigEvent, and
        // neither the container nor the mod event bus was touched for this class.
        assertTrue(Files.exists(tempDir.resolve("managed.conf")));
        assertEquals("localhost", config.host);
        assertEquals("db.local", config.database.host);
        verifyNoInteractions(container);
        verifyNoInteractions(eventBus);
    }

    @Test
    void managedAndNativeConfigsCoexistOnOneManager(@TempDir Path tempDir) {
        ModContainer container = mock(ModContainer.class);
        NeoForgeConfigManager manager = manager(container, mock(IEventBus.class), tempDir);

        manager.load(ManagedServerConfig.class);
        manager.load(ManagedYamlConfig.class);
        manager.load(ServerConfig.class);

        // Only the native one goes to NeoForge; only the managed ones are CoffeeLib's to reload.
        verify(container).registerConfig(eq(ModConfig.Type.COMMON), any(ModConfigSpec.class), eq("server.toml"));
        assertEquals(List.of("managed.conf", "managed-yaml.yml"), manager.managedFiles());
        assertEquals(2, manager.reloadAll());
    }

    @Test
    void manualReloadAppliesAnEditToTheLiveInstance(@TempDir Path tempDir) throws IOException {
        NeoForgeConfigManager manager = manager(mock(ModContainer.class), mock(IEventBus.class), tempDir);
        ManagedServerConfig config = manager.load(ManagedServerConfig.class);

        Path file = tempDir.resolve("managed.conf");
        Files.writeString(file, Files.readString(file).replace("localhost", "prod.example"));

        assertTrue(manager.reload("managed.conf"), "the file name is the key an operator types");
        assertEquals("prod.example", config.host);
        assertFalse(manager.reload("nope.conf"));
    }

    @Test
    void hotReloadIsStartedAndStoppedOnDemand(@TempDir Path tempDir) {
        NeoForgeConfigManager manager = manager(mock(ModContainer.class), mock(IEventBus.class), tempDir);
        manager.load(ManagedServerConfig.class);

        assertFalse(manager.isHotReloadRunning());
        assertTrue(manager.startHotReload());
        assertTrue(manager.isHotReloadRunning());
        assertTrue(manager.stopHotReload());
        assertFalse(manager.isHotReloadRunning());
    }

    @Test
    void aDetachedManagerRefusesNativeConfigsRatherThanFailingLater() {
        ReloadableConfigManager manager = CoffeeLib.detached("examplemod");

        // Registering a ModConfig is only legal during mod construction, and a detached
        // manager has no container to hand NeoForge — so it says so instead of NPE-ing.
        ConfigException e = assertThrows(ConfigException.class, () -> manager.load(ServerConfig.class));
        assertTrue(e.getMessage().contains("@ConfigFile(format = ...)"));
    }

    @Test
    void aNativeConfigGetsTheTomlExtensionNeoForgeExpects(@TempDir Path tempDir) {
        ModContainer container = mock(ModContainer.class);

        manager(container, mock(IEventBus.class), tempDir).load(ServerConfig.class);

        // NeoForge resolves this string as the whole file name, so passing the annotation
        // value raw wrote a file literally called 'server', with no extension.
        verify(container).registerConfig(eq(ModConfig.Type.COMMON), any(ModConfigSpec.class), eq("server.toml"));
    }

    @Test
    void managedConfigsCanLiveOutsideTheLoadersConfigDirectory(@TempDir Path tempDir) {
        Path own = tempDir.resolve("mymod");
        NeoForgeConfigManager manager = new NeoForgeConfigManager(
                "examplemod", mock(ModContainer.class), mock(IEventBus.class), () -> own);

        manager.load(ManagedYamlConfig.class);

        assertTrue(Files.exists(own.resolve("managed-yaml.yml")), "the directory is created on first write");
        assertEquals(List.of("managed-yaml.yml"), manager.managedFiles());
    }

    @Test
    void aManagerIsReachableByModIdForTheCommandToActOn(@TempDir Path tempDir) {
        NeoForgeConfigManager manager = new NeoForgeConfigManager(
                "registrymod", mock(ModContainer.class), mock(IEventBus.class), () -> tempDir);
        manager.load(ManagedYamlConfig.class);

        // /coffeelib config finds services this way: the command lives in CoffeeLib's own
        // jar and the mods it acts on hand it nothing.
        assertTrue(ConfigReloadRegistry.owners().contains("registrymod"));
        assertTrue(ConfigReloadRegistry.of("registrymod").contains(manager));
        assertEquals(List.of("managed-yaml.yml"), ConfigReloadRegistry.of("registrymod").get(0).managedFiles());

        ConfigReloadRegistry.unregister("registrymod", manager);
        assertFalse(ConfigReloadRegistry.owners().contains("registrymod"));
    }
}
