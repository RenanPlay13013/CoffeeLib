package net.loyalnetwork.coffeelib.core.text;

import net.loyalnetwork.coffeelib.api.config.ConfigReloadService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMessagesTest {
    static class Catalog { String welcome = "Welcome {player}, {coins}!"; }
    private static final ConfigReloadService RELOAD = new ConfigReloadService() {
        public List<String> managedFiles() { return List.of(); }
        public boolean reload(String name) { return false; }
        public int reloadAll() { return 0; }
        public boolean startHotReload() { return false; }
        public boolean stopHotReload() { return false; }
        public boolean isHotReloadRunning() { return false; }
        public void addReloadListener(Consumer<Object> listener) { }
    };

    @Test void sendsAndReplacesNamedValues() {
        List<String> sent = new ArrayList<>();
        DefaultMessages<Catalog> messages = new DefaultMessages<>(new Catalog(), RELOAD,
                (to, text) -> sent.add(to + ": " + text));
        messages.send("player", c -> c.welcome, "player", "Ana", "coins", 7);
        assertEquals(List.of("player: Welcome Ana, 7!"), sent);
    }
    @Test void missingKeysRemainVisibleAndValuesAreNotRecursivelyInterpolated() {
        DefaultMessages<Catalog> messages = new DefaultMessages<>(new Catalog(), RELOAD, (a, b) -> {});
        assertEquals("Welcome {coins}, {coins}!", messages.render(c -> c.welcome, "player", "{coins}"));
    }
    @Test void invalidPairsFailBeforeSending() {
        List<String> sent = new ArrayList<>();
        DefaultMessages<Catalog> messages = new DefaultMessages<>(new Catalog(), RELOAD,
                (to, text) -> sent.add(text));
        assertThrows(IllegalArgumentException.class, () -> messages.send("a", c -> c.welcome, "onlyKey"));
        assertThrows(IllegalArgumentException.class, () -> messages.send("a", c -> c.welcome, "key", "a", "key", "b"));
        assertTrue(sent.isEmpty());
    }
    @Test void updatesToLiveCatalogAreSeenWithoutRecreatingTheService() {
        Catalog model = new Catalog();
        DefaultMessages<Catalog> messages = new DefaultMessages<>(model, RELOAD, (a, b) -> {});
        model.welcome = "Revised {player}";
        assertEquals("Revised Ana", messages.render(c -> c.welcome, "player", "Ana"));
    }
}
