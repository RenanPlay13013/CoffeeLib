package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.permission.annotation.Permission;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers command declarations; delegates path/argument invariants to
 * dedicated domain collaborators. An invalid registration never gets published.
 */
public final class CommandScanner {
    private CommandScanner() { }

    public static List<CommandDefinition> scan(String ownerId, Object handler) {
        return scan(ownerId, handler, ArgumentTypes.withDefaults());
    }

    public static List<CommandDefinition> scan(String ownerId, Object handler, ArgumentTypes types) {
        Class<?> type = handler.getClass();
        Command group = type.getAnnotation(Command.class);
        List<List<String>> prefixes = group == null ? List.of(List.of())
                : CommandPaths.paths(group, type.getName());

        List<CommandDefinition> definitions = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            Command command = method.getAnnotation(Command.class);
            if (command == null) continue;
            definitions.addAll(declarations(ownerId, handler, group, prefixes, method, command, types));
        }
        if (definitions.isEmpty()) {
            throw new CoffeeLibException(type.getName() + " was registered as a command handler but declares no "
                    + "@Command methods");
        }
        CommandPaths.requireNoDuplicates(definitions);
        return definitions;
    }

    private static List<CommandDefinition> declarations(String ownerId, Object handler, Command group,
                                                       List<List<String>> prefixes, Method method,
                                                       Command command, ArgumentTypes types) {
        String where = handler.getClass().getName() + "#" + method.getName();
        if (Modifier.isStatic(method.getModifiers())) {
            throw new CoffeeLibException("@Command method " + where + " must not be static");
        }
        method.setAccessible(true);
        String permission = commandPermission(command, group);
        int level = permissionLevel(command, group, permission);
        Permission node = permissionDeclaration(handler.getClass(), method, permission, where);
        Parameter[] parameters = method.getParameters();
        Class<?> senderType = parameters.length > 0 && CommandArguments.isSender(parameters[0], types)
                ? parameters[0].getType() : null;
        List<ParameterDefinition> arguments = CommandArguments.arguments(handler, where, parameters,
                senderType == null ? 0 : 1, types);
        boolean replaces = command.replaces() || (group != null && group.replaces());

        List<CommandDefinition> result = new ArrayList<>();
        for (List<String> prefix : prefixes) {
            for (List<String> own : CommandPaths.paths(command, where)) {
                List<String> path = new ArrayList<>(prefix);
                path.addAll(own);
                if (path.isEmpty()) {
                    throw new CoffeeLibException("@Command method " + where
                            + " has an empty path and its class has no @Command prefix");
                }
                result.add(new CommandDefinition(ownerId, path, permission, level, node,
                        command.description(), replaces, handler, method, senderType, arguments));
            }
        }
        return result;
    }

    private static String commandPermission(Command command, Command group) {
        if (!command.permission().isEmpty()) return command.permission();
        return group == null ? "" : group.permission();
    }

    private static int permissionLevel(Command command, Command group, String permission) {
        if (command.permissionLevel() >= 0) return command.permissionLevel();
        if (group != null && group.permissionLevel() >= 0) return group.permissionLevel();
        return permission.isEmpty() ? 0 : 2;
    }

    private static Permission permissionDeclaration(Class<?> type, Method method,
                                                     String permission, String where) {
        Permission node = method.isAnnotationPresent(Permission.class)
                ? method.getAnnotation(Permission.class) : type.getAnnotation(Permission.class);
        if (node != null && permission.isEmpty()) {
            throw new CoffeeLibException("@Permission on " + where + " sets the default of a node, but the "
                    + "command has no permission: set @Command(permission = ...)");
        }
        return node;
    }

    // Existing package access stays intact for platform-independent tests/callers.
    static ArgumentKind kindOf(Class<?> type) { return CommandArguments.kindOf(type); }
    static void requireNoDuplicates(List<CommandDefinition> definitions) {
        CommandPaths.requireNoDuplicates(definitions);
    }
}
