package net.loyalnetwork.coffeelib.api.text;

import java.util.function.Function;

/**
 * A loaded, reloadable message catalogue owned by one mod or plugin.
 * <p>The template model is a plain config class annotated with {@code @ConfigFile};
 * the config backend retains the original instance when a reload occurs.
 * Rendered placeholders are single-pass, so substituted values do not expand
 * other placeholders or styling until the platform renders the final message.
 * <p>Sending is queued onto the owning server thread by the platform adapter.
 * A recipient invalid by delivery time is silently skipped.
 */
public interface Messages<T> {
    /** Template resolved from the current config values. */
    String render(Function<T, String> template, Object... namesAndValues);

    /** Reloads the catalog's managed config file, updating its live model in place. */
    boolean reload();

    /** Sends to a command source or native platform sender/player. */
    void send(Object recipient, Function<T, String> template, Object... namesAndValues);

    /**
     * {@link #send(Object, Function, Object...)}, with {@code context} given to
     * the {@code %namespace_key%} placeholders of the message. The
     * {@code {name}} pairs are unaffected: they are this message's own ready
     * values, while the context is for placeholders other mods resolve too.
     * CoffeeLib's catalogs implement it; the default is for implementations
     * from elsewhere.
     */
    default void send(Object recipient, net.loyalnetwork.coffeelib.api.placeholder.TextContext context,
                      Function<T, String> template, Object... namesAndValues) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support text contexts");
    }
}
