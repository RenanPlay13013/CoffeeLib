package net.loyalnetwork.coffeelib.api.menu;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Immutable menu specification. Item stacks are copied by the platform when opened. */
public final class MenuSnapshot<S, P> {
    private final int rows;
    private final String title;
    private final Map<Integer, Entry<S, P>> items;

    public MenuSnapshot(int rows, String title, Map<Integer, Entry<S, P>> items) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        this.rows = rows;
        this.title = Objects.requireNonNull(title, "title");
        this.items = Map.copyOf(Objects.requireNonNull(items, "items"));
        for (Integer slot : this.items.keySet()) {
            if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Slot outside chest: " + slot);
        }
    }

    public int rows() { return rows; }
    public String title() { return title; }
    public Map<Integer, Entry<S, P>> items() { return items; }

    /** The stack is a template, never a live player-inventory slot. */
    public record Entry<S, P>(S stack, Consumer<MenuClick<P>> onClick) {
        public Entry {
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(onClick, "onClick");
        }
    }
}
