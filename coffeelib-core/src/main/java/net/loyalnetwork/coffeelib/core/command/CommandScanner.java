package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.SuggestContext;
import net.loyalnetwork.coffeelib.api.command.annotation.Arg;
import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.command.annotation.Default;
import net.loyalnetwork.coffeelib.api.command.annotation.Greedy;
import net.loyalnetwork.coffeelib.api.command.annotation.OrSelf;
import net.loyalnetwork.coffeelib.api.command.annotation.Registry;
import net.loyalnetwork.coffeelib.api.command.annotation.Suggest;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.permission.annotation.Permission;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a handler object's {@code @Command} methods into
 * {@link CommandDefinition}s.
 * <p>
 * All validation happens here, so that a mistake in a handler class is a
 * failure at registration with the method named, not a confused sender or a
 * stack trace the first time someone runs the command. Checked: supported
 * argument types, optional arguments only after required ones, {@code @Greedy}
 * only on a last {@code String}, unique argument names, {@code @Default}
 * values that parse, and that a {@code @Suggest} provider exists with a
 * usable signature. The provider is not called here: it may depend on server
 * state that does not exist yet at registration.
 */
public final class CommandScanner {

    private CommandScanner() {
    }

    /** {@link #scan(String, Object, ArgumentTypes)} with only the core's argument types. */
    public static List<CommandDefinition> scan(String ownerId, Object handler) {
        return scan(ownerId, handler, ArgumentTypes.withDefaults());
    }

    /**
     * @param ownerId the mod or plugin the commands belong to, for log lines
     * @param types   the argument types beyond the built-in ones: the platform's and the owner's
     * @throws CoffeeLibException naming the method when the handler is wrong
     */
    public static List<CommandDefinition> scan(String ownerId, Object handler, ArgumentTypes types) {
        Class<?> type = handler.getClass();
        Command group = type.getAnnotation(Command.class);
        List<List<String>> prefixes = group == null ? List.of(List.of()) : paths(group, type.getName());

        List<CommandDefinition> definitions = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            Command command = method.getAnnotation(Command.class);
            if (command == null) {
                continue;
            }
            String where = type.getName() + "#" + method.getName();
            if (Modifier.isStatic(method.getModifiers())) {
                throw new CoffeeLibException("@Command method " + where + " must not be static");
            }
            method.setAccessible(true);

            String permission = !command.permission().isEmpty() ? command.permission()
                    : group != null ? group.permission() : "";
            int level = command.permissionLevel() >= 0 ? command.permissionLevel()
                    : group != null && group.permissionLevel() >= 0 ? group.permissionLevel()
                    : permission.isEmpty() ? 0 : 2;
            Permission node = method.isAnnotationPresent(Permission.class) ? method.getAnnotation(Permission.class)
                    : type.getAnnotation(Permission.class);
            if (node != null && permission.isEmpty()) {
                throw new CoffeeLibException("@Permission on " + where + " sets the default of a node, but the "
                        + "command has no permission: set @Command(permission = ...)");
            }

            Parameter[] parameters = method.getParameters();
            Class<?> senderType = parameters.length > 0 && isSender(parameters[0], types) ? parameters[0].getType() : null;
            List<ParameterDefinition> arguments = arguments(handler, where, parameters, senderType != null ? 1 : 0,
                    types);
            boolean replaces = command.replaces() || (group != null && group.replaces());

            for (List<String> prefix : prefixes) {
                for (List<String> own : paths(command, where)) {
                    List<String> path = new ArrayList<>(prefix);
                    path.addAll(own);
                    if (path.isEmpty()) {
                        throw new CoffeeLibException("@Command method " + where
                                + " has an empty path and its class has no @Command prefix");
                    }
                    definitions.add(new CommandDefinition(ownerId, path, permission, level, node,
                            command.description(), replaces, handler, method, senderType, arguments));
                }
            }
        }

        if (definitions.isEmpty()) {
            throw new CoffeeLibException(type.getName() + " was registered as a command handler but declares no "
                    + "@Command methods");
        }
        requireNoDuplicates(definitions);
        return definitions;
    }

    /** The main path and every alias, each split into lower-case words. */
    private static List<List<String>> paths(Command command, String where) {
        List<List<String>> paths = new ArrayList<>();
        paths.add(words(command.value(), where));
        for (String alias : command.aliases()) {
            List<String> words = words(alias, where);
            if (words.isEmpty()) {
                throw new CoffeeLibException("@Command alias on " + where + " must not be empty");
            }
            paths.add(words);
        }
        return paths;
    }

    private static List<String> words(String path, String where) {
        List<String> words = new ArrayList<>();
        for (String word : path.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (word.startsWith("/")) {
                throw new CoffeeLibException("@Command path on " + where + " must not start with '/': '" + path + "'");
            }
            words.add(word.toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /**
     * The first parameter is the sender when it is not something an argument
     * could be - a {@code CommandSource}, or a platform type such as a player.
     * A {@code String} first parameter is an argument, never the sender, and
     * so is any first parameter annotated as one ({@code @Arg}, {@code @OrSelf}...):
     * {@code god(@OrSelf ServerPlayer target)} takes a target, not a sender.
     */
    private static boolean isSender(Parameter parameter, ArgumentTypes types) {
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

    private static List<ParameterDefinition> arguments(Object handler, String where, Parameter[] parameters,
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
                    fallback != null ? fallback.value() : null, suggestions(handler, parameter, context), custom,
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

    @SuppressWarnings("unchecked")
    private static ParameterDefinition.Suggester suggestions(Object handler, Parameter parameter, String context) {
        Suggest suggest = parameter.getAnnotation(Suggest.class);
        if (suggest == null) {
            return null;
        }
        if (suggest.provider().isEmpty()) {
            List<String> values = Arrays.asList(suggest.value());
            return new ParameterDefinition.Suggester() {
                @Override
                public Collection<String> suggest(SuggestContext suggestContext) {
                    return values;
                }

                @Override
                public boolean byToken() {
                    return false;
                }
            };
        }

        Method provider = findProvider(handler.getClass(), suggest.provider(), context);
        Type declared = provider.getGenericReturnType();
        if (declared instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class<?> element && element != String.class) {
                throw new CoffeeLibException("@Suggest provider '" + suggest.provider() + "' for " + context
                        + " returns Collection<" + element.getTypeName() + "> instead of Collection<String>");
            }
        }
        provider.setAccessible(true);
        Class<?> takes = provider.getParameterCount() == 1 ? provider.getParameterTypes()[0] : null;
        // CommandSource first, so a provider taking Object keeps the old meaning.
        boolean takesSource = takes != null && takes.isAssignableFrom(CommandSource.class);
        boolean takesContext = takes != null && !takesSource;
        return new ParameterDefinition.Suggester() {
            @Override
            public Collection<String> suggest(SuggestContext suggestContext) {
                try {
                    Object result = takesContext ? provider.invoke(handler, suggestContext)
                            : takesSource ? provider.invoke(handler, suggestContext.source())
                            : provider.invoke(handler);
                    return (Collection<String>) result;
                } catch (IllegalAccessException | InvocationTargetException e) {
                    // A broken provider costs the sender suggestions, not the command.
                    return List.of();
                }
            }

            @Override
            public boolean byToken() {
                return takesContext;
            }
        };
    }

    private static Method findProvider(Class<?> type, String name, String context) {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || !Collection.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() == 0) {
                return method;
            }
            if (method.getParameterCount() == 1) {
                Class<?> takes = method.getParameterTypes()[0];
                if (takes.isAssignableFrom(CommandSource.class) || takes.isAssignableFrom(SuggestContext.class)) {
                    return method;
                }
            }
        }
        throw new CoffeeLibException("@Suggest(provider = \"" + name + "\") on " + context + " names no method "
                + "returning a Collection<String> that takes nothing, a CommandSource or a SuggestContext");
    }

    /**
     * Two executors for the same path would make one of them unreachable on
     * Paper and silently replace the other on Brigadier; refuse instead.
     */
    static void requireNoDuplicates(List<CommandDefinition> definitions) {
        Set<String> seen = new HashSet<>();
        for (CommandDefinition definition : definitions) {
            String key = String.join(" ", definition.path());
            if (!seen.add(key)) {
                throw new CoffeeLibException("More than one @Command method is registered for '/" + key + "'");
            }
        }
    }
}
