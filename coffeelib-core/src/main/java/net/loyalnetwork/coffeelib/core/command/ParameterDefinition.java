package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * One argument of a command: its name, how it is parsed, whether it can be
 * left out, and what tab completion offers for it.
 * <p>
 * Parsing lives here rather than in each platform so that a typo in a number
 * produces the same message everywhere, and so the Paper dispatcher - which
 * has no Brigadier to lean on - parses exactly what the Forge-like ones do.
 */
public final class ParameterDefinition {

    private final String name;
    private final Class<?> type;
    private final ArgumentKind kind;
    private final String defaultValue;
    private final Function<CommandSource, Collection<String>> suggestions;

    ParameterDefinition(String name, Class<?> type, ArgumentKind kind, String defaultValue,
                        Function<CommandSource, Collection<String>> suggestions) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.suggestions = suggestions;
    }

    public String name() {
        return name;
    }

    public Class<?> type() {
        return type;
    }

    public ArgumentKind kind() {
        return kind;
    }

    /** Whether the sender may leave this argument out. */
    public boolean isOptional() {
        return defaultValue != null;
    }

    /** The text to parse when it is left out, or {@code null} for a required argument. */
    public String defaultValue() {
        return defaultValue;
    }

    /** How the argument reads in a usage line: {@code <amount>}, {@code [amount]}, {@code <message...>}. */
    public String usageToken() {
        String body = kind == ArgumentKind.GREEDY ? name + "..." : name;
        return isOptional() ? "[" + body + "]" : "<" + body + ">";
    }

    /**
     * Converts what the sender typed into the parameter's type.
     *
     * @throws CommandException with a message for the sender when it does not parse
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object parse(String input) {
        try {
            return switch (kind) {
                case WORD, GREEDY -> input;
                case INTEGER -> Integer.parseInt(input);
                case LONG -> Long.parseLong(input);
                case DOUBLE -> Double.parseDouble(input);
                case FLOAT -> Float.parseFloat(input);
                case BOOLEAN -> parseBoolean(input);
                case ENUM -> parseEnum((Class<? extends Enum>) type, input);
            };
        } catch (NumberFormatException e) {
            throw new CommandException("'" + input + "' is not a valid " + describeKind() + " for <" + name + ">.");
        }
    }

    private Object parseBoolean(String input) {
        if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("on")) {
            return true;
        }
        if (input.equalsIgnoreCase("false") || input.equalsIgnoreCase("no") || input.equalsIgnoreCase("off")) {
            return false;
        }
        throw new CommandException("'" + input + "' is not true or false, for <" + name + ">.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object parseEnum(Class<? extends Enum> enumType, String input) {
        for (Enum constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(input)) {
                return constant;
            }
        }
        throw new CommandException("'" + input + "' is not one of " + String.join(", ", enumNames())
                + ", for <" + name + ">.");
    }

    private String describeKind() {
        return switch (kind) {
            case INTEGER, LONG -> "whole number";
            case DOUBLE, FLOAT -> "number";
            default -> "value";
        };
    }

    /**
     * Tab-completion candidates starting with {@code prefix}, case-insensitively.
     * Enums and booleans offer their values unless {@code @Suggest} says otherwise.
     */
    public List<String> suggest(CommandSource source, String prefix) {
        Collection<String> candidates;
        if (suggestions != null) {
            candidates = suggestions.apply(source);
        } else if (kind == ArgumentKind.ENUM) {
            candidates = enumNames();
        } else if (kind == ArgumentKind.BOOLEAN) {
            candidates = List.of("true", "false");
        } else {
            return List.of();
        }

        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matching = new ArrayList<>();
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    matching.add(candidate);
                }
            }
        }
        return matching;
    }

    /** Whether this argument has anything to offer for tab completion. */
    public boolean hasSuggestions() {
        return suggestions != null || kind == ArgumentKind.ENUM || kind == ArgumentKind.BOOLEAN;
    }

    private List<String> enumNames() {
        List<String> names = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            names.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
