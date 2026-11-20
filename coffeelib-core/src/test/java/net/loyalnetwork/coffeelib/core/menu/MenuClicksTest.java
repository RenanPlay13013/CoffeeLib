package net.loyalnetwork.coffeelib.core.menu;

import net.loyalnetwork.coffeelib.api.menu.Menu;
import net.loyalnetwork.coffeelib.api.menu.MenuClick;
import net.loyalnetwork.coffeelib.api.menu.MenuClickType;
import net.loyalnetwork.coffeelib.api.menu.MenuSnapshot;
import net.loyalnetwork.coffeelib.core.menu.MenuClicks.Decoded;
import net.loyalnetwork.coffeelib.core.menu.MenuClicks.VanillaKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static net.loyalnetwork.coffeelib.api.menu.MenuClickType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Menu click types: decoding what each platform sends, and which ones an item answers. */
class MenuClicksTest {

    @Test
    void forgeAndNeoForgeClicksByKindAndButton() {
        assertEquals(new Decoded(LEFT, 0), MenuClicks.vanilla(VanillaKind.PICKUP, 0));
        assertEquals(new Decoded(RIGHT, 0), MenuClicks.vanilla(VanillaKind.PICKUP, 1));
        assertEquals(new Decoded(SHIFT_LEFT, 0), MenuClicks.vanilla(VanillaKind.QUICK_MOVE, 0));
        assertEquals(new Decoded(SHIFT_RIGHT, 0), MenuClicks.vanilla(VanillaKind.QUICK_MOVE, 1));
        assertEquals(new Decoded(MIDDLE, 0), MenuClicks.vanilla(VanillaKind.CLONE, 2));
        assertEquals(new Decoded(NUMBER_KEY, 1), MenuClicks.vanilla(VanillaKind.SWAP, 0), "hotbar slot 0 is key 1");
        assertEquals(new Decoded(NUMBER_KEY, 9), MenuClicks.vanilla(VanillaKind.SWAP, 8));
        assertEquals(new Decoded(SWAP_OFFHAND, 0), MenuClicks.vanilla(VanillaKind.SWAP, 40));
        assertEquals(new Decoded(DROP, 0), MenuClicks.vanilla(VanillaKind.THROW, 0));
        assertEquals(new Decoded(CONTROL_DROP, 0), MenuClicks.vanilla(VanillaKind.THROW, 1));
        assertEquals(new Decoded(DOUBLE_CLICK, 0), MenuClicks.vanilla(VanillaKind.PICKUP_ALL, 0));

        assertNull(MenuClicks.vanilla(VanillaKind.QUICK_CRAFT, 0), "a drag is not a click");
        assertNull(MenuClicks.vanilla(VanillaKind.PICKUP, 5));
        assertNull(MenuClicks.vanilla(VanillaKind.SWAP, 12));
    }

    @Test
    void bukkitClicksByName() {
        assertEquals(new Decoded(LEFT, 0), MenuClicks.bukkit("LEFT", -1));
        assertEquals(new Decoded(SHIFT_RIGHT, 0), MenuClicks.bukkit("SHIFT_RIGHT", -1));
        assertEquals(new Decoded(MIDDLE, 0), MenuClicks.bukkit("MIDDLE", -1));
        assertEquals(new Decoded(NUMBER_KEY, 3), MenuClicks.bukkit("NUMBER_KEY", 2));
        assertEquals(new Decoded(SWAP_OFFHAND, 0), MenuClicks.bukkit("SWAP_OFFHAND", -1));
        assertEquals(new Decoded(CONTROL_DROP, 0), MenuClicks.bukkit("CONTROL_DROP", -1));
        assertEquals(new Decoded(DOUBLE_CLICK, 0), MenuClicks.bukkit("DOUBLE_CLICK", -1));

        for (String outside : List.of("WINDOW_BORDER_LEFT", "WINDOW_BORDER_RIGHT", "CREATIVE", "UNKNOWN", "SOMETHING_NEW")) {
            assertNull(MenuClicks.bukkit(outside, -1), outside);
        }
        assertNull(MenuClicks.bukkit("NUMBER_KEY", -1), "a number key with no key");
    }

    @Test
    void clickTypesGroupAsTheirNamesSay() {
        assertTrue(SHIFT_LEFT.isLeft() && SHIFT_LEFT.isShift());
        assertTrue(SHIFT_RIGHT.isRight() && SHIFT_RIGHT.isShift());
        assertFalse(LEFT.isShift());
        assertTrue(MIDDLE.isMiddle());
        assertTrue(CONTROL_DROP.isControl());
        assertFalse(LEFT.isControl(), "the client never sends Ctrl with a mouse click");
        assertTrue(NUMBER_KEY.isKey() && DROP.isKey());
        assertEquals(EnumSet.of(LEFT, RIGHT), MenuClickType.PRIMARY);
    }

    @Test
    void aClickCarriesAHotbarKeyOnlyForNumberKeys() {
        MenuClick<String> click = new MenuClick<>("steve", 4, NUMBER_KEY, 5);
        assertEquals(5, click.hotbarKey());
        assertEquals(new MenuClick<>("steve", 4, LEFT, 0), new MenuClick<>("steve", 4), "the old constructor is a left click");

        assertThrows(IllegalArgumentException.class, () -> new MenuClick<>("steve", 4, NUMBER_KEY, 0));
        assertThrows(IllegalArgumentException.class, () -> new MenuClick<>("steve", 4, LEFT, 3));
        assertThrows(NullPointerException.class, () -> new MenuClick<>("steve", 4, null, 0));
    }

    @Test
    void anItemAnswersOnlyTheClickTypesItWasGiven() {
        AtomicReference<MenuSnapshot<String, String>> opened = new AtomicReference<>();
        List<MenuClickType> heard = new ArrayList<>();
        Menu.<String, String>chest(1, (snapshot, player) -> opened.set(snapshot))
                .item(0, "buy", click -> heard.add(click.type()))
                .item(1, "info", EnumSet.of(SHIFT_LEFT, MIDDLE), click -> heard.add(click.type()))
                .item(2, "any", MenuClickType.ALL, click -> heard.add(click.type()))
                .locked().open("steve");

        MenuSnapshot.Entry<String, String> buy = opened.get().items().get(0);
        assertTrue(buy.accepts(LEFT) && buy.accepts(RIGHT), "the old item(...) still answers plain clicks");
        assertFalse(buy.accepts(SHIFT_LEFT) || buy.accepts(DROP) || buy.accepts(NUMBER_KEY),
                "...and only those, as before click types existed");

        MenuSnapshot.Entry<String, String> info = opened.get().items().get(1);
        assertTrue(info.accepts(SHIFT_LEFT) && info.accepts(MIDDLE));
        assertFalse(info.accepts(LEFT));
        assertTrue(opened.get().items().get(2).accepts(CONTROL_DROP));

        assertThrows(IllegalArgumentException.class, () -> Menu.<String, String>chest(1, (s, p) -> { })
                .item(0, "none", EnumSet.noneOf(MenuClickType.class), click -> { }));
    }
}
