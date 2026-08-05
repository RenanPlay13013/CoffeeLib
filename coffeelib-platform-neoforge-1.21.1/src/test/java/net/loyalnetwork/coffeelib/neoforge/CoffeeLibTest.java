package net.loyalnetwork.coffeelib.neoforge;

import net.loyalnetwork.coffeelib.api.ConfigManager;
import net.loyalnetwork.coffeelib.api.annotation.Comment;
import net.loyalnetwork.coffeelib.api.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.annotation.OneOf;
import net.loyalnetwork.coffeelib.api.annotation.Range;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the real entry point — {@link CoffeeLib#forMod} — against a
 * mocked {@link ModContainer}/{@link IEventBus}. Spec construction (scanning
 * the class, building the {@link ModConfigSpec} tree via push/pop, wiring
 * {@code @Range}/{@code @OneOf}) runs for real — that part needs no running
 * game, it's pure NightConfig underneath, and the resulting spec is what
 * gets asserted against here.
 * <p>
 * What a mock genuinely can't give us is NeoForge's own file-loading
 * lifecycle: {@code ModConfigSpec.acceptConfig} calling into NeoForge's real
 * {@code ConfigTracker}/{@code FMLPaths} the moment anything needs
 * correcting or saving, none of which exist outside a running mod
 * environment. So this stops at "the listener NeoForge will eventually
 * drive is registered for the right event types" rather than faking the
 * full load-from-disk round trip.
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
    void loadRegistersASpecAndKeepsDefaultsUntilNeoForgeLoadsTheFile() {
        ModContainer container = mock(ModContainer.class);
        IEventBus eventBus = mock(IEventBus.class);
        when(container.getEventBus()).thenReturn(eventBus);

        ConfigManager manager = CoffeeLib.forMod(container);
        ServerConfig config = manager.load(ServerConfig.class);

        // Nothing has told NeoForge to actually load the file yet — fields still hold class defaults.
        // Proves scanFields()/defineValue() walked every field (including two levels of nesting and
        // the @OneOf/@Range branches) without throwing, since load() would have failed otherwise.
        assertEquals("localhost", config.host);
        assertEquals("normal", config.difficulty);
        assertEquals("db.local", config.database.host);
        assertEquals(5432, config.database.port);
        assertEquals("root", config.database.credentials.username);
        assertEquals("changeme", config.database.credentials.password);

        verify(container).registerConfig(eq(ModConfig.Type.COMMON), any(ModConfigSpec.class), eq("server"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadRegistersListenersForBothLoadingAndReloadingEvents() {
        ModContainer container = mock(ModContainer.class);
        IEventBus eventBus = mock(IEventBus.class);
        when(container.getEventBus()).thenReturn(eventBus);

        CoffeeLib.forMod(container);

        ArgumentCaptor<Class> classCaptor = ArgumentCaptor.forClass(Class.class);
        verify(eventBus, atLeastOnce()).addListener(classCaptor.capture(), any(Consumer.class));

        List<Class> registeredTypes = classCaptor.getAllValues();
        assertTrue(registeredTypes.contains(ModConfigEvent.Loading.class), "should listen for ModConfigEvent.Loading");
        assertTrue(registeredTypes.contains(ModConfigEvent.Reloading.class), "should listen for ModConfigEvent.Reloading");
    }
}
