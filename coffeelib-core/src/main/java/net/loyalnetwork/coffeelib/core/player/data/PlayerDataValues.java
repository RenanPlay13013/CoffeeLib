package net.loyalnetwork.coffeelib.core.player.data;

import java.util.Objects;

/** Common conversions for formats that expose a persistent string slot. */
public final class PlayerDataValues {
    private PlayerDataValues() { }

    public static String encodeFlag(Boolean value) { return Objects.requireNonNull(value, "flag") ? "1" : "0"; }

    public static Boolean decodeFlag(String value) {
        if (value == null) return false;
        return switch (value) {
            case "1" -> true;
            case "0" -> false;
            default -> throw new IllegalStateException("Corrupted player-data flag: " + value);
        };
    }

    public static String encodeInteger(Integer value) { return Objects.requireNonNull(value, "integer").toString(); }

    public static Integer decodeInteger(String value) {
        if (value == null) return 0;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ex) { throw new IllegalStateException("Corrupted player-data integer: " + value, ex); }
    }

    public static String encodeText(String value) { return Objects.requireNonNull(value, "text"); }

    public static String decodeText(String value) { return value == null ? "" : value; }
}
