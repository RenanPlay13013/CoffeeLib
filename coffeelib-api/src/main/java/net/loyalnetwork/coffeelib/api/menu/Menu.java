package net.loyalnetwork.coffeelib.api.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Small chest-menu builder. Uses native ItemStack/Player types supplied by each platform.
 * The first release supports display-only menus: call {@link #locked()} before opening.
 * Unlocked inventories are rejected rather than risking accidental item duplication.
 * Click callbacks are delivered on the next server tick, not inside inventory transactions.
 */
public final class Menu<S, P> {
    private final MenuOpener<S, P> opener;
    private final int rows;
    private final Map<Integer, MenuSnapshot.Entry<S, P>> items = new HashMap<>();
    private String title = "";
    private boolean locked;

    private Menu(int rows, MenuOpener<S, P> opener) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        this.rows = rows;
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    public static <S, P> Menu<S, P> chest(int rows, MenuOpener<S, P> opener) {
        return new Menu<>(rows, opener);
    }

    public Menu<S, P> title(String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    /** Adds a display item with an optional action. A null action means no callback. */
    public Menu<S, P> item(int slot, S stack, Consumer<MenuClick<P>> callback) {
        checkSlot(slot);
        items.put(slot, new MenuSnapshot.Entry<>(Objects.requireNonNull(stack, "stack"),
                callback == null ? click -> { } : callback));
        return this;
    }

    public Menu<S, P> item(int slot, S stack) { return item(slot, stack, null); }

    /** Mandatory read-only protection; no items can be deposited, withdrawn or moved. */
    public Menu<S, P> locked() {
        this.locked = true;
        return this;
    }

    /** Takes a snapshot, so later builder changes cannot alter an already opened menu. */
    public void open(P player) {
        Objects.requireNonNull(player, "player");
        if (!locked) throw new IllegalStateException("Only locked chest menus are supported");
        opener.open(new MenuSnapshot<>(rows, title, items), player);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Slot " + slot + " is outside the chest");
    }
}
