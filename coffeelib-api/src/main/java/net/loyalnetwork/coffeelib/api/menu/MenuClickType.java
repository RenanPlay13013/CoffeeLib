package net.loyalnetwork.coffeelib.api.menu;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * How a menu slot was clicked, as the client reported it.
 * <p>
 * What reaches the server is decided by the Minecraft client, not by
 * CoffeeLib, and two things players might expect never do:
 * <ul>
 *   <li><b>Middle click</b> is only sent in creative mode; in survival the
 *       client sends nothing at all, so {@link #MIDDLE} never arrives there.</li>
 *   <li><b>Ctrl + click</b> is sent as a plain click: the protocol has no
 *       Ctrl flag. The only Ctrl the server ever sees is Ctrl + drop
 *       ({@link #CONTROL_DROP}). (On macOS, Ctrl + click is a right click.)</li>
 * </ul>
 * {@link #DOUBLE_CLICK} is sent by clients only in some situations - with
 * nothing on the cursor, which is always the case in a locked menu, most
 * clients send two {@link #LEFT}s instead - so do not rely on it.
 */
public enum MenuClickType {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    /** Creative mode only - see the class description. */
    MIDDLE,
    DOUBLE_CLICK,
    /** A hotbar key, 1-9, pressed over the slot; {@link MenuClick#hotbarKey()} says which. */
    NUMBER_KEY,
    /** The swap-hands key (F) pressed over the slot. Not on Minecraft 1.8. */
    SWAP_OFFHAND,
    /** The drop key (Q) pressed over the slot. */
    DROP,
    /** Ctrl + the drop key: the only Ctrl combination the client sends. */
    CONTROL_DROP;

    /** What {@code Menu#item(slot, stack, callback)} accepts: a plain left or right click. */
    public static final Set<MenuClickType> PRIMARY = Collections.unmodifiableSet(EnumSet.of(LEFT, RIGHT));

    /** Every click type. */
    public static final Set<MenuClickType> ALL = Collections.unmodifiableSet(EnumSet.allOf(MenuClickType.class));

    public boolean isLeft() {
        return this == LEFT || this == SHIFT_LEFT || this == DOUBLE_CLICK;
    }

    public boolean isRight() {
        return this == RIGHT || this == SHIFT_RIGHT;
    }

    public boolean isShift() {
        return this == SHIFT_LEFT || this == SHIFT_RIGHT;
    }

    public boolean isMiddle() {
        return this == MIDDLE;
    }

    /** Only {@link #CONTROL_DROP}: the client never sends Ctrl with a mouse click. */
    public boolean isControl() {
        return this == CONTROL_DROP;
    }

    /** A key over the slot rather than a mouse button. */
    public boolean isKey() {
        return this == NUMBER_KEY || this == SWAP_OFFHAND || this == DROP || this == CONTROL_DROP;
    }
}
