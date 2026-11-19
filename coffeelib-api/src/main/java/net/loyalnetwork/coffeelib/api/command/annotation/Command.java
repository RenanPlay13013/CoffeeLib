package net.loyalnetwork.coffeelib.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a method: that method is a command's executor. On a class: a prefix for
 * every {@code @Command} method in it.
 * <p>
 * {@link #value()} is the command path, words separated by spaces, so
 * subcommands need no tree of their own:
 * <pre>{@code
 * @Command("crate")
 * public final class CrateCommands {
 *
 *     @Command("give")                       // /crate give <player> [amount]
 *     void give(CommandSource source, String player, @Default("1") int amount) { ... }
 *
 *     @Command("")                           // /crate on its own
 *     void help(CommandSource source) { ... }
 * }
 * }</pre>
 * The method's parameters after the optional sender are the command's
 * arguments, in order - their types decide how each is parsed and what tab
 * completion offers.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Command {

    /** The path, e.g. {@code "crate give"}. Empty on a method means the class prefix itself. */
    String value();

    /**
     * Other full paths that run the same executor, e.g. {@code "crates give"}.
     * On a class, alternative prefixes.
     */
    String[] aliases() default {};

    /**
     * Permission node required to run this command, on Paper and anything else
     * that has nodes. Empty means none. A class-level permission applies to
     * every method that does not declare its own.
     */
    String permission() default "";

    /**
     * Operator level required on Forge and NeoForge (0 = everyone, 2 = ops,
     * 4 = console only). {@code -1}, the default, derives it: 0 without a
     * {@link #permission()}, 2 with one.
     */
    int permissionLevel() default -1;

    /** One line for usage messages and help. */
    String description() default "";

    /**
     * Takes the root's name over from a command that is not CoffeeLib's - the
     * vanilla {@code /tp}, say - instead of merging with it. The other command
     * is removed when this one is registered, and the replacement is logged.
     * Another CoffeeLib owner's command is never replaced: two owners claiming
     * one root this way fail at {@code register}. On a class, it applies to
     * every method's root. Aliases of the replaced command, such as vanilla's
     * {@code /teleport}, stay.
     */
    boolean replaces() default false;
}
