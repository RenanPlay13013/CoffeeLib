package net.loyalnetwork.coffeelib.api.command;

import java.util.Set;

/**
 * Registers annotation-driven commands with the platform, for one owner (a
 * mod or a plugin).
 * <p>
 * {@link #register(Object)} scans the object for
 * {@link net.loyalnetwork.coffeelib.api.command.annotation.Command} methods
 * and turns each into a real platform command with tab completion: Brigadier
 * nodes on Forge and NeoForge, a dynamically registered command on Paper.
 * Mistakes in a handler class - an optional argument before a required one, an
 * unsupported parameter type, a {@code @Suggest} provider that does not exist -
 * fail at registration, not when someone first runs the command.
 */
public interface CommandManager {

    /** Scans and registers every {@code @Command} method of {@code handler}. */
    void register(Object handler);

    /** The root labels registered so far, e.g. {@code "crate"}. */
    Set<String> registeredRoots();

    /**
     * Lets this owner's handlers take {@code type} as an argument, read by
     * {@code argument}. Call it before registering the handlers that use it.
     * A type the platform already reads (a player, a dimension...) can be
     * overridden this way, for this owner only.
     */
    <T> void argument(Class<T> type, CommandArgument<T> argument);
}
