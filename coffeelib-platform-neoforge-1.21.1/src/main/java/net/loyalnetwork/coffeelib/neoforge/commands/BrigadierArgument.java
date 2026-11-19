package net.loyalnetwork.coffeelib.neoforge.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.minecraft.commands.CommandSourceStack;

/**
 * A {@link CommandArgument} with a native Brigadier parser, compiled to its
 * own node so the client validates and completes it (coordinates with
 * {@code ~}, resource ids...). {@link #parse} is still what reads a
 * {@code @Default}, which comes as text.
 *
 * @param <T> the parameter type
 */
public interface BrigadierArgument<T> extends CommandArgument<T> {

    /** The argument type the node is built with. */
    ArgumentType<?> brigadier();

    /** The value from a context the native parser filled in. */
    T read(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException;

    /** Whether the native parser suggests by itself, client side; when not, CoffeeLib's suggestions are attached. */
    default boolean suggestsNatively() {
        return false;
    }
}
