package net.loyalnetwork.coffeelib.forge;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.api.config.annotation.Comment;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.config.annotation.Range;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CoffeeLib#forMod} and the native path of {@link ForgeConfigManager}
 * both go through Forge's static {@code ModLoadingContext.get()}/
 * {@code FMLJavaModLoadingContext.get()} — thread-local context Forge only
 * populates during real mod construction. Outside that, the only way to
 * exercise this code is intercepting those statics, which Mockito 5's inline
 * mock maker supports directly (no extra setup needed).
 * <p>
 * The managed-format path needs none of that, which is exactly the property
 * worth testing: a config in a CoffeeLib-managed format is read from disk
 * during {@code load}, so these tests assert against real files in a temp
 * directory with no Forge lifecycle involved at all.
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

    @ConfigFile(value = "managed", format = ConfigFormat.YAML)
    public static final class ManagedServerConfig {
        @Comment("Servidor principal")
        public String host = "localhost";

        public DatabaseConfig database = new DatabaseConfig();
    }

    @ConfigFile(value = "managed-json", format = ConfigFormat.JSON)
    public static final class ManagedJsonConfig {
        public String name = "coffee";
    }

    @Test
    void nativeLoadRegistersASpecAndKeepsDefaultsUntilForgeLoadsTheFile() {
        IEventBus eventBus = mock(IEventBus.class);
        ForgeSpecConfigManager manager = new ForgeSpecConfigManager(eventBus);

        ModLoadingContext context = mock(ModLoadingContext.class);
        try (MockedStatic<ModLoadingContext> mocked = mockStatic(ModLoadingContext.class)) {
            mocked.when(ModLoadingContext::get).thenReturn(context);

            ServerConfig config = manager.load(ServerConfig.class);

            // Nothing has told Forge to actually load the file yet — fields still hold class
            // defaults. Proves scanFields()/defineValue() walked every field (including two
            // levels of nesting and the @OneOf/@Range branches) without throwing.
            assertEquals("localhost", config.host);
            assertEquals("normal", config.difficulty);
            assertEquals("db.local", config.database.host);
            assertEquals(5432, config.database.port);
            assertEquals("root", config.database.credentials.username);
            assertEquals("changeme", config.database.credentials.password);

            verify(context).registerConfig(eq(ModConfig.Type.COMMON), any(ForgeConfigSpec.class), eq("server.toml"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theNativeManagerListensForModConfigEvent() {
        IEventBus eventBus = mock(IEventBus.class);
        new ForgeSpecConfigManager(eventBus);

        verify(eventBus).addListener(any(Consumer.class));
    }

    @Test
    void forModWiresUpTheModEventBusFromFMLJavaModLoadingContext() {
        IEventBus eventBus = mock(IEventBus.class);
        FMLJavaModLoadingContext fmlContext = mock(FMLJavaModLoadingContext.class);
        when(fmlContext.getModEventBus()).thenReturn(eventBus);

        ModLoadingContext context = mock(ModLoadingContext.class);
        when(context.getActiveNamespace()).thenReturn("examplemod");

        try (MockedStatic<FMLJavaModLoadingContext> fml = mockStatic(FMLJavaModLoadingContext.class);
             MockedStatic<ModLoadingContext> loading = mockStatic(ModLoadingContext.class)) {
            fml.when(FMLJavaModLoadingContext::get).thenReturn(fmlContext);
            loading.when(ModLoadingContext::get).thenReturn(context);

            ReloadableConfigManager manager = CoffeeLib.forMod();

            assertNotNull(manager);
            verify(fmlContext).getModEventBus();

            // Nothing is registered with Forge, and nothing is managed, until a config is
            // actually loaded — which is what lets a mod build its manager anywhere.
            assertEquals(List.of(), manager.managedFiles());
            assertFalse(manager.isHotReloadRunning());
        }
    }

    @Test
    void aManagedConfigIsPopulatedDuringLoadWithNoModLoadingInvolved(@TempDir Path tempDir) {
        IEventBus eventBus = mock(IEventBus.class);
        ForgeConfigManager manager = new ForgeConfigManager("examplemod", eventBus, () -> tempDir);

        ManagedServerConfig config = manager.load(ManagedServerConfig.class);

        // The file exists and the instance is populated right now: no ModConfigEvent, and
        // the mod event bus was never touched for this class.
        assertTrue(Files.exists(tempDir.resolve("managed.yml")));
        assertEquals("localhost", config.host);
        assertEquals("db.local", config.database.host);
        assertEquals(List.of("managed.yml"), manager.managedFiles());
        verifyNoInteractions(eventBus);
    }

    @Test
    void managedAndNativeConfigsCoexistOnOneManager(@TempDir Path tempDir) {
        IEventBus eventBus = mock(IEventBus.class);
        ForgeConfigManager manager = new ForgeConfigManager("examplemod", eventBus, () -> tempDir);

        ModLoadingContext context = mock(ModLoadingContext.class);
        try (MockedStatic<ModLoadingContext> mocked = mockStatic(ModLoadingContext.class)) {
            mocked.when(ModLoadingContext::get).thenReturn(context);

            manager.load(ManagedServerConfig.class);
            manager.load(ManagedJsonConfig.class);
            manager.load(ServerConfig.class);

            // Only the native one goes to Forge; only the managed ones are CoffeeLib's to reload.
            verify(context).registerConfig(eq(ModConfig.Type.COMMON), any(ForgeConfigSpec.class), eq("server.toml"));
            assertEquals(List.of("managed.yml", "managed-json.json"), manager.managedFiles());
            assertEquals(2, manager.reloadAll());
        }
    }

    @Test
    void manualReloadAppliesAnEditToTheLiveInstance(@TempDir Path tempDir) throws IOException {
        ForgeConfigManager manager = new ForgeConfigManager("examplemod", mock(IEventBus.class), () -> tempDir);
        ManagedServerConfig config = manager.load(ManagedServerConfig.class);

        Path file = tempDir.resolve("managed.yml");
        Files.writeString(file, Files.readString(file).replace("host: localhost", "host: prod.example"));

        assertTrue(manager.reload("managed.yml"), "the file name is the key an operator types");
        assertEquals("prod.example", config.host);
        assertFalse(manager.reload("nope.yml"));
    }

    @Test
    void hotReloadIsStartedAndStoppedOnDemand(@TempDir Path tempDir) {
        ForgeConfigManager manager = new ForgeConfigManager("examplemod", mock(IEventBus.class), () -> tempDir);
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
        // manager has no event bus to hand Forge — so it says so instead of NPE-ing.
        ConfigException e = assertThrows(ConfigException.class, () -> manager.load(ServerConfig.class));
        assertTrue(e.getMessage().contains("@ConfigFile(format = ...)"));
    }

    @Test
    void aNativeConfigGetsTheTomlExtensionForgeExpects() {
        IEventBus eventBus = mock(IEventBus.class);
        ForgeSpecConfigManager manager = new ForgeSpecConfigManager(eventBus);

        ModLoadingContext context = mock(ModLoadingContext.class);
        try (MockedStatic<ModLoadingContext> mocked = mockStatic(ModLoadingContext.class)) {
            mocked.when(ModLoadingContext::get).thenReturn(context);

            manager.load(ServerConfig.class);

            // Forge resolves this string as the whole file name, so passing the annotation
            // value raw wrote a file literally called 'server', with no extension.
            verify(context).registerConfig(eq(ModConfig.Type.COMMON), any(ForgeConfigSpec.class),
                    eq("server.toml"));
        }
    }

    @Test
    void managedConfigsCanLiveOutsideTheLoadersConfigDirectory(@TempDir Path tempDir) {
        Path own = tempDir.resolve("mymod");
        ForgeConfigManager manager = new ForgeConfigManager("examplemod", mock(IEventBus.class), () -> own);

        manager.load(ManagedServerConfig.class);

        assertTrue(Files.exists(own.resolve("managed.yml")), "the directory is created on first write");
        assertEquals(List.of("managed.yml"), manager.managedFiles());
    }

    @Test
    void aManagerIsReachableByModIdForTheCommandToActOn(@TempDir Path tempDir) {
        ForgeConfigManager manager = new ForgeConfigManager("registrymod", mock(IEventBus.class), () -> tempDir);
        manager.load(ManagedServerConfig.class);

        // /coffeelib config finds services this way: the command lives in CoffeeLib's own
        // jar and the mods it acts on hand it nothing.
        assertTrue(ConfigReloadRegistry.owners().contains("registrymod"));
        assertTrue(ConfigReloadRegistry.of("registrymod").contains(manager));
        assertEquals(List.of("managed.yml"), ConfigReloadRegistry.of("registrymod").get(0).managedFiles());

        ConfigReloadRegistry.unregister("registrymod", manager);
        assertFalse(ConfigReloadRegistry.owners().contains("registrymod"));
    }
}
