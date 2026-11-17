package net.loyalnetwork.coffeelib.core.placeholder;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderContext;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;
import net.loyalnetwork.coffeelib.api.placeholder.annotation.Placeholder;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test gets its own registry, so the process-wide one is never polluted. */
class PlaceholderSystemTest {

    /** Stands in for a platform's player type. */
    record FakePlayer(String name, int keys) {
    }

    private static PlaceholderManager manager(PlaceholderRegistry registry, String namespace) {
        return new DefaultPlaceholderManager(registry, namespace + "-owner", namespace, null);
    }

    static final class CratePlaceholders {
        @Placeholder("keys")
        int keys(FakePlayer player, String crate) {
            return crate.isEmpty() ? player.keys() : player.keys() * 10;
        }

        @Placeholder("total")
        long total() {
            return 42;
        }

        @Placeholder("keys_total")
        String keysTotal() {
            return "all-keys";
        }

        @Placeholder("echo")
        String echo(PlaceholderContext context) {
            return context.key() + ":" + context.argument();
        }

        @Placeholder("nothing")
        String nothing() {
            return null;
        }

        @Placeholder("broken")
        String broken() {
            throw new IllegalStateException("resolver bug");
        }
    }

    @Test
    void annotatedMethodsResolveWithPlayerAndArgument() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        manager(registry, "crates").register(new CratePlaceholders());
        FakePlayer steve = new FakePlayer("Steve", 3);

        assertEquals("You have 3 keys", registry.apply(steve, "You have %crates_keys% keys"));
        assertEquals("30", registry.apply(steve, "%crates_keys_daily%"), "argument after the key");
        assertEquals("42 opened", registry.apply(null, "%crates_total% opened"), "no player needed");
        assertEquals("echo:a_b_c", registry.apply(null, "%crates_echo_a_b_c%"), "underscores stay in the argument");
    }

    @Test
    void theLongestKeyWins() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        manager(registry, "crates").register(new CratePlaceholders());

        // "keys_total" is a key of its own, not "keys" with the argument "total".
        assertEquals("all-keys", registry.apply(new FakePlayer("Steve", 3), "%crates_keys_total%"));
    }

    @Test
    void aNamespaceWithAnUnderscoreResolves() {
        // Mod ids may contain "_"; cutting at the first underscore, as PlaceholderAPI does,
        // would look for a namespace "my" and never find this one.
        PlaceholderRegistry registry = new PlaceholderRegistry();
        manager(registry, "my_mod").register("level", context -> "7");
        manager(registry, "my").register("level", context -> "wrong");

        assertEquals("7", registry.apply(null, "%my_mod_level%"));
        assertEquals("wrong", registry.apply(null, "%my_level%"));
    }

    @Test
    void whatCannotBeResolvedStaysAsWritten() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        manager(registry, "crates").register(new CratePlaceholders());

        assertEquals("%other_thing%", registry.apply(null, "%other_thing%"), "unknown namespace");
        assertEquals("%crates_nope%", registry.apply(null, "%crates_nope%"), "unknown key");
        assertEquals("%crates_nothing%", registry.apply(null, "%crates_nothing%"), "resolver returned null");
        assertEquals("%crates_keys%", registry.apply(null, "%crates_keys%"), "needs a player, has none");
        assertEquals("%crates_keys%", registry.apply("not a player", "%crates_keys%"), "wrong kind of player");
    }

    @Test
    void aBrokenResolverCostsOnlyItsOwnPlaceholder() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        manager(registry, "crates").register(new CratePlaceholders());

        assertEquals("%crates_broken% / 42", registry.apply(null, "%crates_broken% / %crates_total%"));
    }

    @Test
    void percentSignsThatAreNotPlaceholdersSurvive() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        manager(registry, "crates").register(new CratePlaceholders());

        assertEquals("50% off, 20% more", registry.apply(null, "50% off, 20% more"));
        assertEquals("100%42", registry.apply(null, "100%%crates_total%"));
        assertEquals("42%", registry.apply(null, "%crates_total%%"));
        assertEquals("%unknown% and 42", registry.apply(null, "%unknown% and %crates_total%"));
        assertEquals("plain text", registry.apply(null, "plain text"));
        assertEquals("", registry.apply(null, ""));
    }

    @Test
    void aMalformedOrTakenKeyIsRefused() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        PlaceholderManager crates = manager(registry, "crates");
        crates.register("total", context -> "1");

        assertThrows(CoffeeLibException.class, () -> crates.register("total", context -> "2"));
        assertThrows(CoffeeLibException.class, () -> crates.register("Bad Key", context -> "x"));
        assertThrows(CoffeeLibException.class, () -> crates.register("has%percent", context -> "x"));
        assertThrows(CoffeeLibException.class, () -> manager(registry, "Bad NS").register("ok", context -> "x"));
    }

    @Test
    void anotherOwnerCannotTakeOverAKey() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        new DefaultPlaceholderManager(registry, "modA", "shared", null).register("value", context -> "a");

        CoffeeLibException e = assertThrows(CoffeeLibException.class,
                () -> new DefaultPlaceholderManager(registry, "modB", "shared", null).register("value", context -> "b"));
        assertTrue(e.getMessage().contains("modA"), e.getMessage());
    }

    @Test
    void unregisterAllDropsOnlyThatOwnersKeys() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        PlaceholderManager modA = new DefaultPlaceholderManager(registry, "modA", "shared", null);
        PlaceholderManager modB = new DefaultPlaceholderManager(registry, "modB", "shared", null);
        modA.register("a", context -> "A");
        modB.register("b", context -> "B");

        modA.unregisterAll();

        assertEquals("%shared_a% B", registry.apply(null, "%shared_a% %shared_b%"));
        assertEquals(Set.of("b"), modB.keys());
    }

    @Test
    void anyManagerAppliesEveryNamespace() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        manager(registry, "economy").register("balance", context -> "$100");
        PlaceholderManager crates = manager(registry, "crates");

        // The whole point: a mod fills in another mod's placeholders through its own manager.
        assertEquals("Balance: $100", crates.apply(null, "Balance: %economy_balance%"));
        assertEquals(Set.of("economy"), registry.namespaces(), "a namespace exists once it has a key");
    }

    static final class BadSignatures {
        @Placeholder("x")
        void returnsNothing() {
        }
    }

    static final class TwoPlayers {
        @Placeholder("x")
        String x(FakePlayer a, FakePlayer b) {
            return "";
        }
    }

    @Test
    void badHandlersFailAtRegistration() {
        PlaceholderManager manager = manager(new PlaceholderRegistry(), "crates");
        assertThrows(CoffeeLibException.class, () -> manager.register(new BadSignatures()));
        assertThrows(CoffeeLibException.class, () -> manager.register(new TwoPlayers()));
        assertThrows(CoffeeLibException.class, () -> manager.register(new Object()));
    }
}
