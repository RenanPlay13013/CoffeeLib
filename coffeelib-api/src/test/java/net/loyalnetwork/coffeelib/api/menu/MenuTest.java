package net.loyalnetwork.coffeelib.api.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MenuTest {
    private record Player(String name) { }
    private record Stack(String id) { }

    @Test void rejectsInvalidRowsAtCreation() {
        assertThrows(IllegalArgumentException.class, () -> Menu.chest(0, (s, p) -> { }));
        assertThrows(IllegalArgumentException.class, () -> Menu.chest(7, (s, p) -> { }));
        assertDoesNotThrow(() -> Menu.chest(1, (s, p) -> { }));
        assertDoesNotThrow(() -> Menu.chest(6, (s, p) -> { }));
    }

    @Test void rejectsInvalidSlotsBeforeOpening() {
        Menu<Stack, Player> menu = Menu.chest(3, (s, p) -> { });
        assertThrows(IllegalArgumentException.class, () -> menu.item(-1, new Stack("x")));
        assertThrows(IllegalArgumentException.class, () -> menu.item(27, new Stack("x")));
        assertDoesNotThrow(() -> menu.item(26, new Stack("x")));
    }

    @Test void rejectsUnlockedInventoriesRatherThanRiskItemLoss() {
        Menu<Stack, Player> menu = Menu.chest(3, (s, p) -> fail("must not open"));
        assertThrows(IllegalStateException.class, () -> menu.open(new Player("steve")));
    }

    @Test void exposesAnImmutableSnapshotAndDoesNotMutateOldOpenings() {
        List<MenuSnapshot<Stack, Player>> opened = new ArrayList<>();
        Menu<Stack, Player> menu = Menu.<Stack, Player>chest(3, (s, p) -> opened.add(s)).locked().title("Kits");
        menu.item(13, new Stack("first"));
        menu.open(new Player("A"));
        menu.item(13, new Stack("second"));
        menu.item(14, new Stack("third"));
        menu.open(new Player("B"));
        assertEquals("first", opened.get(0).items().get(13).stack().id());
        assertEquals(1, opened.get(0).items().size());
        assertEquals(2, opened.get(1).items().size());
        assertThrows(UnsupportedOperationException.class, () -> opened.get(0).items().clear());
    }

    @Test void allowsOptionalCallbacksAndPreservesSlotAndPlayerContext() {
        AtomicReference<MenuClick<Player>> received = new AtomicReference<>();
        AtomicReference<MenuSnapshot<Stack, Player>> captured = new AtomicReference<>();
        Menu<Stack, Player> menu = Menu.<Stack, Player>chest(1, (s, p) -> captured.set(s)).locked();
        menu.item(2, new Stack("button"), received::set).item(5, new Stack("decoration"));
        Player viewer = new Player("Player");
        menu.open(viewer);
        captured.get().items().get(2).onClick().accept(new MenuClick<>(viewer, 2));
        assertEquals(viewer, received.get().player());
        assertEquals(2, received.get().slot());
        assertDoesNotThrow(() -> captured.get().items().get(5).onClick().accept(new MenuClick<>(viewer, 5)));
    }

    @Test void rejectsNullReferencesAndSnapshotOutsideSlots() {
        assertThrows(NullPointerException.class, () -> Menu.chest(1, null));
        Menu<Stack, Player> menu = Menu.chest(1, (s, p) -> { });
        assertThrows(NullPointerException.class, () -> menu.title(null));
        assertThrows(NullPointerException.class, () -> menu.item(0, null));
        assertThrows(NullPointerException.class, () -> menu.locked().open(null));
        assertThrows(IllegalArgumentException.class, () -> new MenuSnapshot<>(1, "", java.util.Map.of(9,
                new MenuSnapshot.Entry<>(new Stack("a"), event -> { }))));
    }
}
