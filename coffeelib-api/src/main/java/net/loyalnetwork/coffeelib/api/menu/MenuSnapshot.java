package net.loyalnetwork.coffeelib.api.menu;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Immutable menu specification. Item stacks are copied by the platform when opened. */
public final class MenuSnapshot<S, P> {
    private final int rows;
    private final String title;
    private final Map<Integer, Entry<S, P>> items;
    private final net.loyalnetwork.coffeelib.api.placeholder.TextContext context;

    public MenuSnapshot(int rows, String title, Map<Integer, Entry<S, P>> items) {
        this(rows, title, items, net.loyalnetwork.coffeelib.api.placeholder.TextContext.EMPTY);
    }

    /** @param context what the menu is about, for its title's placeholders */
    public MenuSnapshot(int rows, String title, Map<Integer, Entry<S, P>> items,
                        net.loyalnetwork.coffeelib.api.placeholder.TextContext context) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        this.rows = rows;
        this.title = Objects.requireNonNull(title, "title");
        this.items = Map.copyOf(Objects.requireNonNull(items, "items"));
        this.context = Objects.requireNonNull(context, "context");
        for (Integer slot : this.items.keySet()) {
            if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Slot outside chest: " + slot);
        }
    }

    public int rows() { return rows; }
    public String title() { return title; }
    public Map<Integer, Entry<S, P>> items() { return items; }
    /** What the menu is about; the platform resolves the title's placeholders with it on open. */
    public net.loyalnetwork.coffeelib.api.placeholder.TextContext context() { return context; }

    /** The stack is a template, never a live player-inventory slot. */
    public record Entry<S, P>(S stack, Consumer<MenuClick<P>> onClick) {
        public Entry {
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(onClick, "onClick");
        }
    }
}
