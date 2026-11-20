package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.annotation.Arg;
import net.loyalnetwork.coffeelib.api.command.annotation.Default;
import net.loyalnetwork.coffeelib.api.command.annotation.Greedy;
import net.loyalnetwork.coffeelib.api.command.annotation.OrSelf;
import net.loyalnetwork.coffeelib.api.command.annotation.Registry;
import net.loyalnetwork.coffeelib.api.command.annotation.Suggest;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Interpretation and validation of handler parameters, independent of path registration. */
final class CommandArguments {
    private CommandArguments() { }

    /**
     * The first parameter is the sender when it is not something an argument
     * could be - a {@code CommandSource}, or a platform type such as a player.
     * A {@code String} first parameter is an argument, never the sender, and
     * so is any first parameter annotated as one ({@code @Arg}, {@code @OrSelf}...):
     * {@code god(@OrSelf ServerPlayer target)} takes a target, not a sender.
     */
    static boolean isSender(Parameter parameter, ArgumentTypes types) {
        if (CommandSource.class.isAssignableFrom(parameter.getType())) {
            return true;
        }
        for (Class<? extends java.lang.annotation.Annotation> marker : List.of(Arg.class, OrSelf.class,
                Default.class, Suggest.class, Greedy.class, Registry.class)) {
            if (parameter.isAnnotationPresent(marker)) {
                return false;
            }
        }
        // Players are an intentional exception: a leading, unannotated player
        // is traditionally the command's sender, even when it is a supported
        // command argument. An @Arg/@OrSelf forces player-target semantics.
        if (types.isPlayer(parameter.getType())) {
            return true;
        }
        // Built-in and registered custom types (UUID, Duration, Kit, etc.)
        // are arguments, not 'sender' values. Previously a bare first UUID
        // was misclassified as the sender and failed only when executing.
        return kindOf(parameter.getType()) == null && types.find(parameter) == null;
    }

    static List<ParameterDefinition> arguments(Object handler, String where, Parameter[] parameters,
                                                        int first, ArgumentTypes types) {
        List<ParameterDefinition> arguments = new ArrayList<>();
        Set<String> names = new HashSet<>();
        boolean optionalSeen = false;

        for (int i = first; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String name = nameOf(parameter, i - first);
            String context = "argument <" + name + "> of " + where;

            ArgumentKind kind = kindOf(parameter.getType());
            CommandArgument<?> custom = null;
            if (kind == null) {
                custom = types.find(parameter);
                if (custom == null) {
                    throw new CoffeeLibException("Unsupported type " + parameter.getType().getSimpleName() + " for "
                            + context + ". Supported: String, int, long, double, float, boolean (and their wrappers), "
                            + "enums, " + types.names() + ", and types given to CommandManager.argument(...). "
                            + "Only the first parameter may be a sender type.");
                }
                kind = ArgumentKind.CUSTOM;
            }

            OrSelf orSelf = parameter.getAnnotation(OrSelf.class);
            if (orSelf != null) {
                if (!types.isPlayer(parameter.getType())) {
                    throw new CoffeeLibException("@OrSelf only applies to this platform's player type, not to "
                            + context);
                }
                if (parameter.isAnnotationPresent(Default.class)) {
                    throw new CoffeeLibException("@OrSelf " + context + " is already optional; remove its @Default");
                }
                if (i != parameters.length - 1) {
                    throw new CoffeeLibException("@OrSelf " + context + " must be the last argument");
                }
            }

            if (custom != null && custom.words() < 1) {
                throw new CoffeeLibException("Custom " + context + " has an invalid words() count of "
                        + custom.words() + "; expected at least 1");
            }

            if (parameter.isAnnotationPresent(Greedy.class)) {
                if (kind != ArgumentKind.WORD) {
                    throw new CoffeeLibException("@Greedy only applies to String, not to " + context);
                }
                if (i != parameters.length - 1) {
                    throw new CoffeeLibException("@Greedy " + context + " must be the last argument");
                }
                kind = ArgumentKind.GREEDY;
            }

            Default fallback = parameter.getAnnotation(Default.class);
            if (fallback != null || orSelf != null) {
                optionalSeen = true;
            } else if (optionalSeen) {
                throw new CoffeeLibException("Required " + context + " comes after an optional one; "
                        + "optional arguments must be last, or the input would be ambiguous");
            }

            if (!names.add(name)) {
                throw new CoffeeLibException("Two arguments of " + where + " are both called <" + name
                        + ">; give them distinct names with @Arg");
            }

            ParameterDefinition definition = new ParameterDefinition(name, parameter.getType(), kind,
                    fallback != null ? fallback.value() : null, CommandSuggestions.from(handler, parameter, context), custom,
                    orSelf != null ? orSelf.others() : null);
            if (fallback != null && kind != ArgumentKind.CUSTOM) {
                // Fail at registration, not on the first run that leaves it out. A custom type may need
                // the sender or the server to parse, so its default is only parsed when it is used.
                definition.parse(fallback.value());
            }
            arguments.add(definition);
        }
        return arguments;
    }

    private static String nameOf(Parameter parameter, int index) {
        Arg arg = parameter.getAnnotation(Arg.class);
        if (arg != null) {
            return arg.value();
        }
        return parameter.isNamePresent() ? parameter.getName() : "arg" + index;
    }

    /** The parse kind for {@code type}, or {@code null} when it cannot be an argument. */
    static ArgumentKind kindOf(Class<?> type) {
        if (type == String.class) {
            return ArgumentKind.WORD;
        }
        if (type == int.class || type == Integer.class) {
            return ArgumentKind.INTEGER;
        }
        if (type == long.class || type == Long.class) {
            return ArgumentKind.LONG;
        }
        if (type == double.class || type == Double.class) {
            return ArgumentKind.DOUBLE;
        }
        if (type == float.class || type == Float.class) {
            return ArgumentKind.FLOAT;
        }
        if (type == boolean.class || type == Boolean.class) {
            return ArgumentKind.BOOLEAN;
        }
        if (type.isEnum()) {
            return ArgumentKind.ENUM;
        }
        return null;
    }

}
