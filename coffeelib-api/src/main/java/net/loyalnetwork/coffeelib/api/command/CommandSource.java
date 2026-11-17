package net.loyalnetwork.coffeelib.api.command;

/**
 * Whoever ran a command, the same way on every platform.
 * <p>
 * Declare it as an executor's first parameter to get it. For anything this
 * does not cover, declare the native type instead - {@code ServerPlayer} or
 * {@code CommandSourceStack} on Forge/NeoForge, {@code Player} or
 * {@code CommandSender} on Paper - and CoffeeLib passes that, refusing the
 * command with a message when the sender is not one (the console running a
 * player-only command, say).
 */
public interface CommandSource {

    /** A display name: the player's name, or something like "Server" for the console. */
    String name();

    /** Sends a plain-text line back to the sender. */
    void sendMessage(String message);

    /** Whether the sender holds {@code permission}, by this platform's rules. Empty always passes. */
    boolean hasPermission(String permission);

    /** The player behind this source, in the platform's own type, or {@code null} if it is not a player. */
    Object player();

    /** Whether {@link #player()} is non-null. */
    default boolean isPlayer() {
        return player() != null;
    }

    /** The platform's own source object - {@code CommandSourceStack} or {@code CommandSender}. */
    Object unwrap();
}
