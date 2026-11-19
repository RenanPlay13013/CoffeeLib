package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.SuggestContext;

import java.util.function.Function;

/**
 * A {@link SuggestContext} over the argument's text so far; earlier arguments
 * come from a platform function, since only the platform knows how they were
 * parsed (Brigadier's context, or the words already typed).
 */
public final class DefaultSuggestContext implements SuggestContext {

    private final CommandSource source;
    private final String input;
    private final int tokenStart;
    private final int tokenIndex;
    private final Function<String, Object> previous;

    /**
     * @param input    the argument's text up to the cursor
     * @param previous an earlier argument by name, parsed, or {@code null}
     */
    public DefaultSuggestContext(CommandSource source, String input, Function<String, Object> previous) {
        this.source = source;
        this.input = input;
        this.tokenStart = input.lastIndexOf(' ') + 1;
        this.tokenIndex = countWords(input.substring(0, tokenStart));
        this.previous = previous;
    }

    private static int countWords(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    @Override
    public CommandSource source() {
        return source;
    }

    @Override
    public String input() {
        return input;
    }

    @Override
    public String token() {
        return input.substring(tokenStart);
    }

    @Override
    public int tokenIndex() {
        return tokenIndex;
    }

    /** Where {@link #token()} starts within {@link #input()}: what a platform offsets suggestions by. */
    public int tokenStart() {
        return tokenStart;
    }

    @Override
    public <T> T previous(String name, Class<T> type) {
        Object value;
        try {
            value = previous.apply(name);
        } catch (RuntimeException e) {
            return null;
        }
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
