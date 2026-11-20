package net.loyalnetwork.coffeelib.core.menu;

import net.loyalnetwork.coffeelib.api.menu.MenuClickType;

/**
 * Native container clicks turned into {@link MenuClickType}s, kept here so the
 * rules - which button means what - are written once and tested without a game.
 */
public final class MenuClicks {

    /** A decoded click: its type, and the hotbar key (1-9) for a number key, else 0. */
    public record Decoded(MenuClickType type, int hotbarKey) {
    }

    /**
     * Minecraft's own container click kinds (Forge and NeoForge's
     * {@code ClickType}). Each platform maps its enum constant here with a
     * {@code switch} - never by {@code name()}, which Forge 1.20.1 obfuscates
     * in production.
     */
    public enum VanillaKind { PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL }

    /** Vanilla's button for the swap-hands key in a {@code SWAP} click; 0-8 are the hotbar. */
    private static final int OFFHAND_BUTTON = 40;

    private MenuClicks() {
    }

    /** A Forge/NeoForge click, or {@code null} for what is not a click on a slot (a drag). */
    public static Decoded vanilla(VanillaKind kind, int button) {
        return switch (kind) {
            case PICKUP -> button == 0 ? of(MenuClickType.LEFT) : button == 1 ? of(MenuClickType.RIGHT) : null;
            case QUICK_MOVE -> button == 0 ? of(MenuClickType.SHIFT_LEFT)
                    : button == 1 ? of(MenuClickType.SHIFT_RIGHT) : null;
            case SWAP -> button >= 0 && button <= 8 ? new Decoded(MenuClickType.NUMBER_KEY, button + 1)
                    : button == OFFHAND_BUTTON ? of(MenuClickType.SWAP_OFFHAND) : null;
            case CLONE -> of(MenuClickType.MIDDLE);
            case THROW -> button == 0 ? of(MenuClickType.DROP) : button == 1 ? of(MenuClickType.CONTROL_DROP) : null;
            case PICKUP_ALL -> of(MenuClickType.DOUBLE_CLICK);
            case QUICK_CRAFT -> null; // dragging across slots, not a click on one
        };
    }

    /**
     * A Bukkit {@code ClickType} by name - stable across versions, and names
     * that do not exist on 1.8 (SWAP_OFFHAND) still work - or {@code null} for
     * clicks outside the slots (WINDOW_BORDER_*), CREATIVE and UNKNOWN.
     *
     * @param hotbarButton Bukkit's {@code getHotbarButton()}: 0-8 for a number key
     */
    public static Decoded bukkit(String clickType, int hotbarButton) {
        return switch (clickType) {
            case "LEFT" -> of(MenuClickType.LEFT);
            case "RIGHT" -> of(MenuClickType.RIGHT);
            case "SHIFT_LEFT" -> of(MenuClickType.SHIFT_LEFT);
            case "SHIFT_RIGHT" -> of(MenuClickType.SHIFT_RIGHT);
            case "MIDDLE" -> of(MenuClickType.MIDDLE);
            case "DOUBLE_CLICK" -> of(MenuClickType.DOUBLE_CLICK);
            case "NUMBER_KEY" -> hotbarButton >= 0 && hotbarButton <= 8
                    ? new Decoded(MenuClickType.NUMBER_KEY, hotbarButton + 1) : null;
            case "SWAP_OFFHAND" -> of(MenuClickType.SWAP_OFFHAND);
            case "DROP" -> of(MenuClickType.DROP);
            case "CONTROL_DROP" -> of(MenuClickType.CONTROL_DROP);
            default -> null;
        };
    }

    private static Decoded of(MenuClickType type) {
        return new Decoded(type, 0);
    }
}
