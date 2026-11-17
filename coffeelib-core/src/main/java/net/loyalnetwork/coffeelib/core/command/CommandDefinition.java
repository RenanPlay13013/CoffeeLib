package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One executable command: a path such as {@code ["crate", "give"]}, the
 * arguments that follow it, and the method that runs it.
 * <p>
 * Produced by {@link CommandScanner} and consumed by the platforms, which only
 * have to turn the path and arguments into their own nodes and then call
 * {@link #execute}. Everything about running the method - which sender it gets,
 * defaults for missing arguments, the permission check, telling a user error
 * from a bug - happens here, once, for every platform.
 */
public final class CommandDefinition {

    private static final Logger LOGGER = Logger.getLogger(CommandDefinition.class.getName());

    static final String INTERNAL_ERROR = "An internal error occurred while running this command.";

    private final String ownerId;
    private final List<String> path;
    private final String permission;
    private final int permissionLevel;
    private final String description;
    private final Object handler;
    private final Method method;
    private final Class<?> senderType;
    private final List<ParameterDefinition> parameters;

    CommandDefinition(String ownerId, List<String> path, String permission, int permissionLevel,
                      String description, Object handler, Method method, Class<?> senderType,
                      List<ParameterDefinition> parameters) {
        this.ownerId = ownerId;
        this.path = List.copyOf(path);
        this.permission = permission;
        this.permissionLevel = permissionLevel;
        this.description = description;
        this.handler = handler;
        this.method = method;
        this.senderType = senderType;
        this.parameters = List.copyOf(parameters);
    }

    /** The literal words, lower case, root first. */
    public List<String> path() {
        return path;
    }

    public String root() {
        return path.get(0);
    }

    /** The node a permission check applies to; empty for none. */
    public String permission() {
        return permission;
    }

    /** The operator level Forge-like platforms require (already derived, never {@code -1}). */
    public int permissionLevel() {
        return permissionLevel;
    }

    public String description() {
        return description;
    }

    public List<ParameterDefinition> parameters() {
        return parameters;
    }

    /** How many arguments must be given, i.e. those before the first optional one. */
    public int requiredCount() {
        int count = 0;
        for (ParameterDefinition parameter : parameters) {
            if (parameter.isOptional()) {
                break;
            }
            count++;
        }
        return count;
    }

    /** {@code /crate give <player> [amount]}. */
    public String usage() {
        StringBuilder usage = new StringBuilder("/").append(String.join(" ", path));
        for (ParameterDefinition parameter : parameters) {
            usage.append(' ').append(parameter.usageToken());
        }
        return usage.toString();
    }

    /**
     * Runs the command with arguments as the sender typed them, one string per
     * argument (the platform has already joined a greedy one). Missing trailing
     * arguments are allowed down to {@link #requiredCount()}.
     *
     * @throws CommandException for anything the sender should be told
     */
    public void execute(CommandSource source, List<String> rawArguments) {
        if (rawArguments.size() < requiredCount() || rawArguments.size() > parameters.size()) {
            throw new CommandException("Usage: " + usage());
        }
        Object[] values = new Object[parameters.size()];
        for (int i = 0; i < rawArguments.size(); i++) {
            values[i] = rawArguments.get(i);
        }
        executeParsed(source, values);
    }

    /**
     * Runs the command with arguments that may already be typed - Brigadier
     * hands over an {@code Integer} for an integer node - or still strings, or
     * {@code null} for an optional one that was left out. Strings are parsed,
     * nulls get the default.
     *
     * @throws CommandException for anything the sender should be told
     */
    public void executeParsed(CommandSource source, Object[] values) {
        if (!source.hasPermission(permission)) {
            throw new CommandException("You do not have permission to run this command.");
        }

        Object sender = senderType == null ? null : resolveSender(source);

        Object[] arguments = new Object[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            ParameterDefinition parameter = parameters.get(i);
            Object value = i < values.length ? values[i] : null;
            if (value == null) {
                if (!parameter.isOptional()) {
                    throw new CommandException("Usage: " + usage());
                }
                value = parameter.defaultValue();
            }
            // Strings are what the sender typed (or a @Default); anything else was already
            // parsed by the platform, such as an Integer from a Brigadier integer node.
            arguments[i] = value instanceof String text ? parameter.parse(text) : value;
        }

        Object[] call;
        if (senderType == null) {
            call = arguments;
        } else {
            call = new Object[arguments.length + 1];
            call[0] = sender;
            System.arraycopy(arguments, 0, call, 1, arguments.length);
        }
        invoke(source, call);
    }

    /**
     * The value for the executor's sender parameter: the {@code CommandSource}
     * itself, the platform's source object, or the player - whichever the
     * method declared - or a refusal if this sender is none of them.
     */
    private Object resolveSender(CommandSource source) {
        if (senderType.isInstance(source)) {
            return source;
        }
        Object platform = source.unwrap();
        if (platform != null && senderType.isInstance(platform)) {
            return platform;
        }
        Object player = source.player();
        if (player != null && senderType.isInstance(player)) {
            return player;
        }
        throw new CommandException(senderType.getSimpleName().contains("Player")
                ? "Only players can run this command."
                : "This command cannot be run from here.");
    }

    private void invoke(CommandSource source, Object[] call) {
        try {
            method.invoke(handler, call);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CommandException userError) {
                throw userError;
            }
            // A bug in the executor: the sender learns that it failed, the log learns why.
            LOGGER.log(Level.SEVERE, "[CoffeeLib/" + ownerId + "] Command '" + String.join(" ", path)
                    + "' failed for " + source.name(), cause);
            throw new CommandException(INTERNAL_ERROR);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.SEVERE, "[CoffeeLib/" + ownerId + "] Could not call " + method, e);
            throw new CommandException(INTERNAL_ERROR);
        }
    }
}
