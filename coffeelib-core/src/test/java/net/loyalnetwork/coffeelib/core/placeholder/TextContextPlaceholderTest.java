package net.loyalnetwork.coffeelib.core.placeholder;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.menu.Menu;
import net.loyalnetwork.coffeelib.api.menu.MenuSnapshot;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderContext;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;
import net.loyalnetwork.coffeelib.api.placeholder.TextContext;
import net.loyalnetwork.coffeelib.api.placeholder.annotation.Context;
import net.loyalnetwork.coffeelib.api.placeholder.annotation.Placeholder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item 17: placeholders resolved with what the text is about. */
class TextContextPlaceholderTest {

    /** The platform's player, as far as these tests go. */
    static class FakePlayer {
        final String name;

        FakePlayer(String name) {
            this.name = name;
        }
    }

    record Kit(String name, int uses) {
    }

    record Crate(String name) {
    }

    interface Named {
        String name();
    }

    record Warp(String name) implements Named {
    }

    record Home(String name) implements Named {
    }

    /** A kits mod: plural with the kit in the argument, singular about "the kit of this text". */
    public static final class KitPlaceholders {
        @Placeholder("kit_name")
        String name(@Context Kit kit) {
            return kit.name();
        }

        @Placeholder("kit_remaining")
        String remaining(FakePlayer player, @Context Kit kit) {
            return player.name + ":" + kit.uses();
        }

        @Placeholder("kit_in")
        String in(@Context Kit kit, @Context Crate crate) {
            return kit.name() + "@" + crate.name();
        }

        @Placeholder("named")
        String named(@Context Named named) {
            return named.name();
        }

        @Placeholder("seen")
        String seen(PlaceholderContext context) {
            return context.text().get(Kit.class).map(Kit::name).orElse("none");
        }
    }

    private final PlaceholderRegistry registry = new PlaceholderRegistry();
    private final Kit vip = new Kit("vip", 3);

    private PlaceholderManager manager(String namespace) {
        return new DefaultPlaceholderManager(registry, "kitsmod", namespace, null);
    }

    @AfterEach
    void resetPlayerTypes() {
        SharedPlaceholders.playerTypes();
    }

    @Test
    void aContextValueReachesTheMethodAndWithoutItTheTextStaysAsWritten() {
        manager("kits").register(new KitPlaceholders());

        assertEquals("Kit vip", registry.apply(null, "Kit %kits_kit_name%", TextContext.of(vip)));
        assertEquals("Kit %kits_kit_name%", registry.apply(null, "Kit %kits_kit_name%"));
        assertEquals("Kit %kits_kit_name%", registry.apply(null, "Kit %kits_kit_name%", TextContext.of(new Crate("c"))));
    }

    @Test
    void playerAndContextAreBothRequiredWhenBothAreDeclared() {
        manager("kits").register(new KitPlaceholders());
        FakePlayer steve = new FakePlayer("Steve");

        assertEquals("Steve:3", registry.apply(steve, "%kits_kit_remaining%", TextContext.of(vip)));
        assertEquals("%kits_kit_remaining%", registry.apply(steve, "%kits_kit_remaining%", TextContext.EMPTY));
        assertEquals("%kits_kit_remaining%", registry.apply(null, "%kits_kit_remaining%", TextContext.of(vip)));
    }

    @Test
    void severalContextTypesInOneMethod() {
        manager("kits").register(new KitPlaceholders());

        assertEquals("vip@daily", registry.apply(null, "%kits_kit_in%", TextContext.of(vip, new Crate("daily"))));
        assertEquals("%kits_kit_in%", registry.apply(null, "%kits_kit_in%", TextContext.of(vip)));
    }

    @Test
    void aValueIsFoundByItsSupertypeButNotWhenTwoWouldDo() {
        manager("kits").register(new KitPlaceholders());

        assertEquals("spawn", registry.apply(null, "%kits_named%", TextContext.of(new Warp("spawn"))));
        assertEquals("%kits_named%", registry.apply(null, "%kits_named%",
                TextContext.of(new Warp("spawn"), new Home("base"))), "two Named values: no guess");
    }

    @Test
    void anotherNamespaceInTheSameTextSeesTheContextToo() {
        manager("kits").register(new KitPlaceholders());
        new DefaultPlaceholderManager(registry, "scoreboard", "board", null)
                .register("kit", context -> context.text().get(Kit.class).map(Kit::name).orElse(null));

        assertEquals("vip / vip / vip", registry.apply(null, "%kits_kit_name% / %board_kit% / %kits_seen%",
                TextContext.of(vip)));
    }

    @Test
    void applyWithoutAContextIsUnchanged() {
        manager("kits").register(new KitPlaceholders());
        manager("crates").register("total", context -> "12");

        assertEquals("12 crates, none, %kits_kit_name%, 50% off",
                registry.apply(null, "%crates_total% crates, %kits_seen%, %kits_kit_name%, 50% off"));
        assertEquals(registry.apply(null, "%crates_total%"), registry.apply(null, "%crates_total%", TextContext.EMPTY));
    }

    @Test
    void thePlaceholderApiRouteHasNoContextSoItNeverResolves() {
        manager("kits").register(new KitPlaceholders());

        // PapiBridge calls exactly this: namespace already split off, no context to give.
        assertEquals(null, registry.resolve("kits", "kit_name", null));
        assertEquals("vip", registry.resolve("kits", "kit_name", null, TextContext.of(vip)));
    }

    @Test
    void theParseCommandCanTellWhatNeedsAContext() {
        manager("kits").register(new KitPlaceholders());
        manager("crates").register("total", context -> "12");

        String parsed = registry.apply(null, "%kits_kit_name% %crates_total% %kits_seen% %nobody_x%");

        assertEquals(List.of("%kits_kit_name%"), registry.needingContext(parsed));
    }

    // --- registration errors, with the method's name

    public static final class ContextOnString {
        @Placeholder("bad")
        String bad(@Context String text) {
            return text;
        }
    }

    public static final class ContextOnPrimitive {
        @Placeholder("bad")
        String bad(@Context int uses) {
            return "" + uses;
        }
    }

    public static final class ContextOnPlaceholderContext {
        @Placeholder("bad")
        String bad(@Context PlaceholderContext context) {
            return "";
        }
    }

    public static final class ContextOnPlayer {
        @Placeholder("bad")
        String bad(@Context FakePlayer player) {
            return "";
        }
    }

    public static final class ContextSameTypeAsPlayer {
        @Placeholder("bad")
        String bad(Kit asPlayer, @Context Kit kit) {
            return "";
        }
    }

    public static final class TwoContextsOfOneType {
        @Placeholder("bad")
        String bad(@Context Kit a, @Context Kit b) {
            return "";
        }
    }

    @Test
    void contextParametersThatCanNeverWorkFailAtRegistrationNamingTheMethod() {
        SharedPlaceholders.playerTypes(FakePlayer.class);
        for (Object handler : List.of(new ContextOnString(), new ContextOnPrimitive(), new ContextOnPlaceholderContext(),
                new ContextOnPlayer(), new ContextSameTypeAsPlayer(), new TwoContextsOfOneType())) {
            CoffeeLibException refused = assertThrows(CoffeeLibException.class, () -> manager("bad").register(handler),
                    handler.getClass().getSimpleName());
            assertTrue(refused.getMessage().contains(handler.getClass().getName() + "#bad"), refused.getMessage());
        }
        assertTrue(registry.keysOf("bad").isEmpty(), "nothing half-registered");
    }

    // --- TextContext itself

    @Test
    void textContextHoldsOneValuePerClassAndNoNulls() {
        assertThrows(IllegalArgumentException.class, () -> TextContext.of(vip, new Kit("other", 1)));
        assertThrows(IllegalArgumentException.class, () -> TextContext.of(vip, null));
        assertThrows(IllegalArgumentException.class, () -> TextContext.EMPTY.with(null));
        assertSame(TextContext.EMPTY, TextContext.of());

        TextContext both = TextContext.of(vip).with(new Crate("daily"));
        TextContext replaced = both.with(new Kit("starter", 1));
        assertEquals(Optional.of(new Kit("starter", 1)), replaced.get(Kit.class), "with replaces the same class");
        assertEquals(Optional.of(new Crate("daily")), replaced.get(Crate.class));
        assertEquals(Optional.of(vip), both.get(Kit.class), "the original is unchanged");
        assertEquals(Optional.of(new Warp("spawn")), TextContext.of(new Warp("spawn")).get(Named.class), "by subtype");

        TextContext twoNamed = TextContext.of(new Warp("spawn"), new Home("base"));
        assertTrue(twoNamed.isAmbiguous(Named.class));
        assertEquals(Optional.empty(), twoNamed.get(Named.class));
        assertEquals(Optional.empty(), twoNamed.get(Object.class));
        assertFalse(twoNamed.isAmbiguous(Warp.class));
    }

    // --- Menu: the title is read on open, with the menu's context

    @Test
    void aMenuTitleIsResolvedWithItsContextWhenOpened() {
        manager("kits").register(new KitPlaceholders());
        AtomicReference<String> openedTitle = new AtomicReference<>();
        // A platform's opener: what NeoForgeMenus/ForgeMenus/PaperMenus do with the snapshot.
        Menu<String, FakePlayer> menu = Menu.chest(1, (MenuSnapshot<String, FakePlayer> snapshot, FakePlayer viewer) ->
                openedTitle.set(registry.apply(viewer, snapshot.title(), snapshot.context())));

        menu.title("Preview: %kits_kit_name%").context(TextContext.of(vip)).locked().open(new FakePlayer("Steve"));

        assertEquals("Preview: vip", openedTitle.get());
    }
}
