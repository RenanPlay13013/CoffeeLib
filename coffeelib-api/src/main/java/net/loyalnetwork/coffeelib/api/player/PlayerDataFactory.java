package net.loyalnetwork.coffeelib.api.player;

/**
 * Declares typed persistent player values for one plugin/mod owner.
 * Register each key once, at mod construction or plugin enable time.
 * Keys are lowercase [a-z0-9._-] and unique within an owner.
 *
 * <p>Defaults: false for flags, 0 for integers, empty string for text,
 * and null for absent JSON values. A null JSON value removes the key.
 * All set calls persist through server saves. Changing a key's type in
 * future releases is a storage migration, not an automatic conversion.
 *
 * <p>{@code copyOnDeath(false)} (the default) clears the key when a
 * player dies and respawns. A dimension change preserves values. Calling
 * {@code copyOnDeath(true)} creates a registration view; it does not change
 * previously registered values.
 */
public interface PlayerDataFactory {
    PlayerDataFactory copyOnDeath(boolean enabled);
    PlayerData<Boolean> flag(String key);
    PlayerData<Integer> integer(String key);
    PlayerData<String> text(String key);
    <T> PlayerData<T> json(String key, Class<T> type);
}
