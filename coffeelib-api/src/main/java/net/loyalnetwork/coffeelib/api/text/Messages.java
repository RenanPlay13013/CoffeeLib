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
}
