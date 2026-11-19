package net.loyalnetwork.coffeelib.api.command;

/**
 * What a suggestion provider can see about the argument being typed.
 * <p>
 * A provider method may take this instead of nothing or a
 * {@link CommandSource}; it then suggests for {@link #token()}, the word under
 * the cursor, which matters for a {@code @Greedy} argument holding several:
 * <pre>{@code
 * Collection<String> coords(SuggestContext context) {
 *     return context.tokenIndex() < 3 ? List.of("~") : dimensionIds();
 * }
 * }</pre>
 * Suggestions from a provider taking this replace only the token. Providers
 * taking nothing or a {@code CommandSource} keep their behavior: matched
 * against, and replacing, the whole argument.
 */
public interface SuggestContext {

    CommandSource source();

    /** The argument's text up to the cursor; for a {@code @Greedy} one, everything since it began. */
    String input();

    /** The word being typed: the last word of {@link #input()}, possibly empty. */
    String token();

    /** Which word of the argument {@link #token()} is: 0, then 1, 2... ({@code @Greedy} and multi-word types). */
    int tokenIndex();

    /**
     * An earlier argument of the same command, as the executor would receive
     * it, or {@code null} when it was not typed or does not parse.
     */
    <T> T previous(String name, Class<T> type);
}
