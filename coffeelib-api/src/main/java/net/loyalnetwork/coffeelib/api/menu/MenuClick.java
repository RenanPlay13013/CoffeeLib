package net.loyalnetwork.coffeelib.api.menu;

import java.util.Objects;

/**
 * An action from a read-only chest menu; callbacks always run on the server thread.
 *
 * @param type      how the slot was clicked - see {@link MenuClickType} for what
 *                  the client can and cannot send
 * @param hotbarKey for {@link MenuClickType#NUMBER_KEY}, the key pressed (1-9);
 *                  0 for every other type
 */
public record MenuClick<P>(P player, int slot, MenuClickType type, int hotbarKey) {

    public MenuClick {
        Objects.requireNonNull(type, "type");
        if (type == MenuClickType.NUMBER_KEY ? hotbarKey < 1 || hotbarKey > 9 : hotbarKey != 0) {
            throw new IllegalArgumentException("hotbarKey " + hotbarKey + " does not fit a " + type + " click");
        }
    }

    /** A plain left click - what every click was before click types existed. */
    public MenuClick(P player, int slot) {
        this(player, slot, MenuClickType.LEFT, 0);
    }

    public MenuClick(P player, int slot, MenuClickType type) {
        this(player, slot, type, 0);
    }

    public boolean isLeft() {
        return type.isLeft();
    }

    public boolean isRight() {
        return type.isRight();
    }

    public boolean isShift() {
        return type.isShift();
    }

    public boolean isMiddle() {
        return type.isMiddle();
    }
}
