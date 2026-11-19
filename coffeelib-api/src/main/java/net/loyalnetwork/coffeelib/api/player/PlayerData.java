package net.loyalnetwork.coffeelib.api.player;

/**
 * One named, persistent value on a server-side player. Does not hold a player
 * reference: use the current player object after respawn or dimension travel.
 * get/set must run on the platform's server thread; unsupported or stale
 * players fail explicitly. Data is saved by the underlying platform, not
 * synchronized to clients by CoffeeLib.
 *
 * @param <T> value type
 */
public interface PlayerData<T> {
    T get(Object player);
    void set(Object player, T value);
}
