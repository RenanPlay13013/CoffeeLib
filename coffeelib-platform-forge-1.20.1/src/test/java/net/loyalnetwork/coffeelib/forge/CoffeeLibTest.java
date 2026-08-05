package net.loyalnetwork.coffeelib.forge;

import net.loyalnetwork.coffeelib.api.ConfigManager;
import net.loyalnetwork.coffeelib.api.annotation.Comment;
import net.loyalnetwork.coffeelib.api.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.annotation.Range;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CoffeeLib#forMod} and {@link ForgeConfigManager#load} both go
 * through Forge's static {@code ModLoadingContext.get()}/
 * {@code FMLJavaModLoadingContext.get()} — thread-local context Forge only
 * populates during real mod construction. Outside that, the only way to
 * exercise this code is intercepting those statics, which Mockito 5's
 * inline mock maker supports directly (no extra setup needed).
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

    @Test
    void loadRegistersASpecAndKeepsDefaultsUntilForgeLoadsTheFile() {
        IEventBus eventBus = mock(IEventBus.class);
        ForgeConfigManager manager = new ForgeConfigManager(eventBus);

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

            verify(context).registerConfig(eq(ModConfig.Type.COMMON), any(ForgeConfigSpec.class), eq("server"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructorRegistersAListenerForModConfigEvent() {
        IEventBus eventBus = mock(IEventBus.class);
        new ForgeConfigManager(eventBus);

        verify(eventBus).addListener(any(Consumer.class));
    }

    @Test
    void forModWiresUpTheModEventBusFromFMLJavaModLoadingContext() {
        IEventBus eventBus = mock(IEventBus.class);
        FMLJavaModLoadingContext fmlContext = mock(FMLJavaModLoadingContext.class);
        when(fmlContext.getModEventBus()).thenReturn(eventBus);

        try (MockedStatic<FMLJavaModLoadingContext> mocked = mockStatic(FMLJavaModLoadingContext.class)) {
            mocked.when(FMLJavaModLoadingContext::get).thenReturn(fmlContext);

            ConfigManager manager = CoffeeLib.forMod();

            assertNotNull(manager);
            verify(fmlContext).getModEventBus();
        }
    }
}
