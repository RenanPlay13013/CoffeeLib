package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.SuggestContext;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One argument of a command: its name, how it is parsed, whether it can be
 * left out, and what tab completion offers for it.
 * <p>
 * Parsing lives here rather than in each platform so that a typo in a number
 * produces the same message everywhere, and so the Paper dispatcher - which
 * has no Brigadier to lean on - parses exactly what the Forge-like ones do.
 */
public final class ParameterDefinition {

    /** A {@code @Suggest} source: fixed values or a provider method. */
    interface Suggester {
        Collection<String> suggest(SuggestContext context);

        /** True for a provider taking a {@link SuggestContext}: it suggests for the token, not the whole argument. */
        boolean byToken();
    }

    private final String name;
    private final Class<?> type;
    private final ArgumentKind kind;
    private final String defaultValue;
    private final Suggester suggester;
    private final CommandArgument<?> custom;
    private final String othersPermission;

    ParameterDefinition(String name, Class<?> type, ArgumentKind kind, String defaultValue, Suggester suggester,
                        CommandArgument<?> custom, String othersPermission) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.suggester = suggester;
        this.custom = custom;
        this.othersPermission = othersPermission;
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

    /** The reader of a {@link ArgumentKind#CUSTOM} argument, or {@code null} for the built-in kinds. */
    public CommandArgument<?> custom() {
        return custom;
    }

    /** How many words the argument takes; a greedy one takes the rest, whatever this says. */
    public int words() {
        return custom != null ? Math.max(1, custom.words()) : 1;
    }

    /** Whether the sender may leave this argument out. */
    public boolean isOptional() {
        return defaultValue != null || isOrSelf();
    }

    /** The text to parse when it is left out, or {@code null} (required, or {@code @OrSelf}). */
    public String defaultValue() {
        return defaultValue;
    }

    /** Whether it is an {@code @OrSelf} player: left out, it is the sender. */
    public boolean isOrSelf() {
        return othersPermission != null;
    }

    /** {@code @OrSelf}'s node for naming someone else; empty for none, {@code null} when not {@code @OrSelf}. */
    public String othersPermission() {
        return othersPermission;
    }

    /** How the argument reads in a usage line: {@code <amount>}, {@code [amount]}, {@code <message...>}. */
    public String usageToken() {
        String body = kind == ArgumentKind.GREEDY ? name + "..." : name;
        return isOptional() ? "[" + body + "]" : "<" + body + ">";
    }

    /** {@link #parse(CommandSource, String)} with no sender, for the built-in kinds' defaults. */
    public Object parse(String input) {
        return parse(null, input);
    }

    /**
     * Converts what the sender typed into the parameter's type.
     *
     * @throws CommandException with a message for the sender when it does not parse
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object parse(CommandSource source, String input) {
        try {
            return switch (kind) {
                case WORD, GREEDY -> input;
                case INTEGER -> Integer.parseInt(input);
                case LONG -> Long.parseLong(input);
                case DOUBLE -> Double.parseDouble(input);
                case FLOAT -> Float.parseFloat(input);
                case BOOLEAN -> parseBoolean(input);
                case ENUM -> parseEnum((Class<? extends Enum>) type, input);
                case CUSTOM -> custom.parse(source, input);
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
     * Tab-completion candidates starting with {@code prefix}, case-insensitively:
     * {@link #suggest(SuggestContext, boolean)} for a context holding just it.
     */
    public List<String> suggest(CommandSource source, String prefix) {
        return suggest(new DefaultSuggestContext(source, prefix, name -> null), false);
    }

    /**
     * Tab-completion candidates: the {@code @Suggest} values or provider, plus
     * what a {@code CommandArgument} offers, or an enum's or boolean's values.
     * Kept are those starting with the word being typed when {@code byToken} or
     * {@link #suggestsByToken()}, with the whole argument otherwise; a
     * namespaced candidate also matches by its path. An {@code @OrSelf}
     * argument offers nothing to a sender who may not name others.
     *
     * @param byToken match the token whatever the provider is - what a platform
     *                that only completes the last word (Paper) needs
     */
    public List<String> suggest(SuggestContext context, boolean byToken) {
        if (isOrSelf() && !othersPermission.isEmpty() && context.source() != null
                && !context.source().hasPermission(othersPermission)) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        if (suggester != null) {
            addAll(candidates, suggester.suggest(context));
        }
        if (custom != null) {
            addAll(candidates, custom.suggest(context));
        } else if (suggester == null && kind == ArgumentKind.ENUM) {
            candidates.addAll(enumNames());
        } else if (suggester == null && kind == ArgumentKind.BOOLEAN) {
            candidates.addAll(List.of("true", "false"));
        }

        String typed = (byToken || suggestsByToken() ? context.token() : context.input()).toLowerCase(Locale.ROOT);
        List<String> matching = new ArrayList<>();
        for (String candidate : candidates) {
            if (matches(candidate.toLowerCase(Locale.ROOT), typed)) {
                matching.add(candidate);
            }
        }
        return matching;
    }

    private static void addAll(Set<String> target, Collection<String> values) {
        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    target.add(value);
                }
            }
        }
    }

    private static boolean matches(String candidate, String typed) {
        if (candidate.startsWith(typed)) {
            return true;
        }
        int colon = candidate.indexOf(':');
        return colon >= 0 && typed.indexOf(':') < 0 && candidate.substring(colon + 1).startsWith(typed);
    }

    /**
     * Whether this argument's suggestions stand for the word being typed rather
     * than the whole argument: a {@code CommandArgument}'s, or a provider taking
     * a {@link SuggestContext}. The platform offsets them to that word.
     */
    public boolean suggestsByToken() {
        return custom != null || (suggester != null && suggester.byToken());
    }

    /** Whether this argument has anything to offer for tab completion. */
    public boolean hasSuggestions() {
        return suggester != null || custom != null || kind == ArgumentKind.ENUM || kind == ArgumentKind.BOOLEAN;
    }

    /** Whether a {@code @Suggest} was given: a platform keeps a native parser's own suggestions otherwise. */
    public boolean hasSuggestAnnotation() {
        return suggester != null;
    }

    private List<String> enumNames() {
        List<String> names = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            names.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
