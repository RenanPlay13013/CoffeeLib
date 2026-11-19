package net.loyalnetwork.coffeelib.api.command;

import java.util.Collection;
import java.util.List;

/**
 * Teaches the command system to read one more parameter type, beyond the
 * strings, numbers, booleans and enums it reads by itself.
 * <pre>{@code
 * commands.argument(Kit.class, new CommandArgument<>() {
 *     public Kit parse(CommandSource source, String input) {
 *         return kits.find(input).orElseThrow(() -> new CommandException("No kit called " + input + "."));
 *     }
 *
 *     public Collection<String> suggest(SuggestContext context) {
 *         return kits.names();
 *     }
 * });
 *
 * @Command("kit")
 * void kit(ServerPlayer player, @Arg("kit") Kit kit) { ... }
 * }</pre>
 * Each platform already brings its own: players, worlds/dimensions and more -
 * see the README. A type registered with
 * {@link CommandManager#argument} applies to that owner's handlers.
 *
 * @param <T> the parameter type
 */
public interface CommandArgument<T> {

    /**
     * Converts what the sender typed - a {@link #words()}-word string - into
     * the value the executor receives.
     *
     * @throws net.loyalnetwork.coffeelib.api.command.exception.CommandException
     *         with the message for the sender when it does not parse
     */
    T parse(CommandSource source, String input);

    /**
     * Candidates for the word being typed ({@link SuggestContext#token()}).
     * CoffeeLib keeps those that start with it, ignoring case; a namespaced
     * candidate ({@code minecraft:sharpness}) also matches by its path.
     */
    default Collection<String> suggest(SuggestContext context) {
        return List.of();
    }

    /**
     * How many space-separated words the argument takes. Only platform types
     * with a native parser (coordinates on Forge and NeoForge) may take more
     * than one there; elsewhere, more than one is read as that many words.
     */
    default int words() {
        return 1;
    }
}
