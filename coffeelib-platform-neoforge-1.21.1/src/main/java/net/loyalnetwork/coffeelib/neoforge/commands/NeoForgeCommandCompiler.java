package net.loyalnetwork.coffeelib.neoforge.commands;

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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;
import net.loyalnetwork.coffeelib.core.command.ArgumentKind;
import net.loyalnetwork.coffeelib.core.command.AsyncCommands;
import net.loyalnetwork.coffeelib.core.command.CommandDefinition;
import net.loyalnetwork.coffeelib.core.command.DefaultSuggestContext;
import net.loyalnetwork.coffeelib.core.command.ParameterDefinition;
import net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers;
import net.loyalnetwork.coffeelib.neoforge.permission.NeoForgePermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletionStage;

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
public final class NeoForgeCommandCompiler {

    private NeoForgeCommandCompiler() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> compile(CommandDefinition definition) {
        List<ParameterDefinition> parameters = definition.parameters();
        int required = definition.requiredCount();

        ArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = parameters.size() - 1; i >= 0; i--) {
            ParameterDefinition parameter = parameters.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> node =
                    Commands.argument(parameter.name(), typeFor(definition, parameter, i == parameters.size() - 1));
            if (attachesSuggestions(parameter)) {
                node.suggests(suggestions(definition, parameter, parameters.subList(0, i)));
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
                // The node through the permission chain (LuckPerms, then its default), with the
                // op level as what "operators" means - and as the whole check when there is no node.
                int level = definition.permissionLevel();
                String node = definition.permission();
                literal.requires(source -> NeoForgePermissions.check(source, node, level));
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

    /**
     * The node's argument type: a native parser for a {@link BrigadierArgument},
     * a word otherwise. A non-native type of several words is read as the rest
     * of the line, so it has to be the last argument.
     */
    private static ArgumentType<?> typeFor(CommandDefinition definition, ParameterDefinition parameter,
                                           boolean last) {
        if (parameter.kind() == ArgumentKind.CUSTOM) {
            if (parameter.custom() instanceof BrigadierArgument<?> nativeType) {
                return nativeType.brigadier();
            }
            if (parameter.words() > 1) {
                if (!last) {
                    throw new CoffeeLibException("Argument <" + parameter.name() + "> of " + definition.usage()
                            + " takes " + parameter.words() + " words with no native parser here, so it must be "
                            + "the last argument");
                }
                return StringArgumentType.greedyString();
            }
            return StringArgumentType.word();
        }
        return switch (parameter.kind()) {
            case WORD, ENUM, CUSTOM -> StringArgumentType.word();
            case GREEDY -> StringArgumentType.greedyString();
            case INTEGER -> IntegerArgumentType.integer();
            case LONG -> LongArgumentType.longArg();
            case DOUBLE -> DoubleArgumentType.doubleArg();
            case FLOAT -> FloatArgumentType.floatArg();
            case BOOLEAN -> BoolArgumentType.bool();
        };
    }

    /** Native parsers keep their own client-side suggestions unless {@code @Suggest} says otherwise. */
    private static boolean attachesSuggestions(ParameterDefinition parameter) {
        if (parameter.custom() instanceof BrigadierArgument<?> nativeType && nativeType.suggestsNatively()) {
            return parameter.hasSuggestAnnotation();
        }
        return parameter.hasSuggestions() && parameter.kind() != ArgumentKind.BOOLEAN;
    }

    private static Class<?> valueClass(ArgumentKind kind) {
        return switch (kind) {
            case WORD, ENUM, GREEDY, CUSTOM -> String.class;
            case INTEGER -> Integer.class;
            case LONG -> Long.class;
            case DOUBLE -> Double.class;
            case FLOAT -> Float.class;
            case BOOLEAN -> Boolean.class;
        };
    }

    /**
     * Suggestions from the core, for the word being typed when the argument
     * suggests by token - offset to that word, so only it is replaced - or
     * for the whole argument otherwise. Earlier arguments are read from the
     * context, for {@code SuggestContext#previous}.
     */
    private static SuggestionProvider<CommandSourceStack> suggestions(CommandDefinition definition,
                                                                      ParameterDefinition parameter,
                                                                      List<ParameterDefinition> earlier) {
        return (context, builder) -> {
            NeoForgeCommandContext source = new NeoForgeCommandContext(context.getSource(),
                    definition.permissionLevel());
            DefaultSuggestContext suggestContext = new DefaultSuggestContext(source, builder.getRemaining(),
                    name -> earlierValue(context, source, earlier, name));
            SuggestionsBuilder target = parameter.suggestsByToken()
                    ? builder.createOffset(builder.getStart() + suggestContext.tokenStart()) : builder;
            for (String suggestion : parameter.suggest(suggestContext, false)) {
                target.suggest(suggestion);
            }
            return target.buildFuture();
        };
    }

    /** An earlier argument as the executor would get it, or {@code null} when it is absent or invalid. */
    private static Object earlierValue(CommandContext<CommandSourceStack> context, NeoForgeCommandContext source,
                                       List<ParameterDefinition> earlier, String name) {
        for (ParameterDefinition parameter : earlier) {
            if (parameter.name().equals(name)) {
                try {
                    return value(context, source, parameter);
                } catch (IllegalArgumentException | CommandSyntaxException | CommandException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** One argument's value from the context: a native parser's result, or the typed value for the core to parse. */
    private static Object value(CommandContext<CommandSourceStack> context, NeoForgeCommandContext source,
                                ParameterDefinition parameter) throws CommandSyntaxException {
        if (parameter.custom() instanceof BrigadierArgument<?> nativeType) {
            return nativeType.read(context, parameter.name());
        }
        Object raw = context.getArgument(parameter.name(), valueClass(parameter.kind()));
        return raw instanceof String text ? parameter.parse(source, text) : raw;
    }

    /** Runs with the first {@code given} arguments present; the rest are optional and left out. */
    private static int run(CommandDefinition definition, CommandContext<CommandSourceStack> context, int given) {
        List<ParameterDefinition> parameters = definition.parameters();
        CommandSourceStack stack = context.getSource();
        NeoForgeCommandContext source = new NeoForgeCommandContext(stack, definition.permissionLevel());
        Object[] values = new Object[parameters.size()];
        try {
            for (int i = 0; i < given; i++) {
                ParameterDefinition parameter = parameters.get(i);
                values[i] = parameter.custom() instanceof BrigadierArgument<?> nativeType
                        ? nativeType.read(context, parameter.name())
                        : context.getArgument(parameter.name(), valueClass(parameter.kind()));
            }
        } catch (CommandSyntaxException e) {
            stack.sendFailure(Component.literal(e.getMessage()));
            return 0;
        } catch (CommandException e) {
            stack.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        try {
            CompletionStage<?> pending = definition.executeParsed(source, values);
            if (pending != null) {
                // Brigadier cannot wait: the command counts as started, and its reply comes later.
                AsyncCommands.watch(definition, source, pending,
                        TickSchedulers.forOwner(definition.ownerId()).main(),
                        message -> stack.sendFailure(Component.literal(message)));
            }
            return 1;
        } catch (CommandException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }
}
