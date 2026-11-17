package net.loyalnetwork.coffeelib.api.placeholder;

/**
 * What a placeholder is being resolved for.
 * <p>
 * For {@code %crates_keys_daily%}, registered as key {@code "keys"} in
 * namespace {@code "crates"}: {@link #key()} is {@code "keys"} and
 * {@link #argument()} is {@code "daily"}.
 */
public interface PlaceholderContext {

    /**
     * The player the text is for, in the platform's own type -
     * {@code ServerPlayer} on Forge/NeoForge, {@code Player} or
     * {@code OfflinePlayer} on Paper - or {@code null} when the text is not
     * about anyone (a server-wide announcement, say).
     */
    Object player();

    /** The registered key that matched. */
    String key();

    /** What followed the key and an underscore, or an empty string. */
    String argument();
}
