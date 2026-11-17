package net.loyalnetwork.coffeelib.forge.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;
import net.loyalnetwork.coffeelib.core.command.ArgumentKind;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.ParameterDefinition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Turns a {@link CommandDefinition} into a Brigadier tree.
 * <p>
 * Compiling to Brigadier rather than registering one opaque node is what gets
 * real tab completion: the client sees each argument's type, so it validates a
 * number as you type it, and argument suggestions are answered by the server
 * through the suggestion providers attached here.
 * <p>
 * Shape for {@code /crate give <player> [amount]}:
 * {@code literal(crate) -> literal(give).requires(level) -> argument(player)
 * .executes -> argument(amount).executes}. An executor sits on every node from
 * the last required argument on, which is how optional arguments work in
 * Brigadier. Trees for the same root merge when registered, so subcommands of
 * one root can come from different methods.
 */
public final class ForgeCommandCompiler {

    private ForgeCommandCompiler() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> compile(CommandDefinition definition) {
        List<ParameterDefinition> parameters = definition.parameters();
        int required = definition.requiredCount();

        ArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = parameters.size() - 1; i >= 0; i--) {
            ParameterDefinition parameter = parameters.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> node =
                    Commands.argument(parameter.name(), typeFor(parameter.kind()));
            if (parameter.hasSuggestions() && parameter.kind() != ArgumentKind.BOOLEAN) {
                node.suggests(suggestions(definition, parameter));
            }
            int given = i + 1;
            if (given >= required) {
                node.executes(context -> run(definition, context, given));
            }
            if (tail != null) {
                node.then(tail);
            }
            tail = node;
        }

        List<String> path = definition.path();
        for (int j = path.size() - 1; j >= 0; j--) {
            LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(path.get(j));
            if (j == path.size() - 1) {
                // On the deepest literal, not the root: trees for one root merge, and a
                // requirement on the shared root would hide every sibling subcommand.
                int level = definition.permissionLevel();
                literal.requires(source -> source.hasPermission(level));
                if (required == 0) {
                    literal.executes(context -> run(definition, context, 0));
                }
            }
            if (tail != null) {
                literal.then(tail);
            }
            tail = literal;
        }
        return (LiteralArgumentBuilder<CommandSourceStack>) tail;
    }

    private static ArgumentType<?> typeFor(ArgumentKind kind) {
        return switch (kind) {
            case WORD, ENUM -> StringArgumentType.word();
            case GREEDY -> StringArgumentType.greedyString();
            case INTEGER -> IntegerArgumentType.integer();
            case LONG -> LongArgumentType.longArg();
            case DOUBLE -> DoubleArgumentType.doubleArg();
            case FLOAT -> FloatArgumentType.floatArg();
            case BOOLEAN -> BoolArgumentType.bool();
        };
    }

    private static Class<?> valueClass(ArgumentKind kind) {
        return switch (kind) {
            case WORD, ENUM, GREEDY -> String.class;
            case INTEGER -> Integer.class;
            case LONG -> Long.class;
            case DOUBLE -> Double.class;
            case FLOAT -> Float.class;
            case BOOLEAN -> Boolean.class;
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestions(CommandDefinition definition,
                                                                      ParameterDefinition parameter) {
        return (context, builder) -> {
            ForgeCommandContext source = new ForgeCommandContext(context.getSource(),
                    definition.permissionLevel());
            for (String suggestion : parameter.suggest(source, builder.getRemaining())) {
                builder.suggest(suggestion);
            }
            return builder.buildFuture();
        };
    }

    /** Runs with the first {@code given} arguments present; the rest are optional and left out. */
    private static int run(CommandDefinition definition, CommandContext<CommandSourceStack> context, int given) {
        List<ParameterDefinition> parameters = definition.parameters();
        Object[] values = new Object[parameters.size()];
        for (int i = 0; i < given; i++) {
            ParameterDefinition parameter = parameters.get(i);
            values[i] = context.getArgument(parameter.name(), valueClass(parameter.kind()));
        }
        try {
            definition.executeParsed(new ForgeCommandContext(context.getSource(), definition.permissionLevel()),
                    values);
            return 1;
        } catch (CommandException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }
}
