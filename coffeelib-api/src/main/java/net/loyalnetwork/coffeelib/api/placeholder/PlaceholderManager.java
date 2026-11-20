package net.loyalnetwork.coffeelib.api.placeholder;

import java.util.Set;

/**
 * One owner's placeholders, and the way to fill in anyone's.
 * <p>
 * Placeholders are written {@code %namespace_key%} or
 * {@code %namespace_key_argument%} - the same syntax as PlaceholderAPI, so a
 * text written for one platform works on the others. Registering puts a
 * placeholder in a registry every mod and plugin on CoffeeLib shares, which is
 * what lets one mod use another's placeholders on Forge and NeoForge, where
 * no common placeholder system exists. On Paper with PlaceholderAPI installed,
 * each namespace is also published as a PlaceholderAPI expansion, and
 * {@link #apply} goes through PlaceholderAPI, so placeholders flow both ways.
 * <p>
 * A placeholder nobody registered, or one whose resolver returned
 * {@code null} or threw, stays in the text as written.
 */
public interface PlaceholderManager {

    /** The namespace this manager registers under, e.g. {@code "crates"}. */
    String namespace();

    /**
     * Registers {@code %namespace_key%}.
     *
     * @throws net.loyalnetwork.coffeelib.api.exception.CoffeeLibException if
     *         the key is malformed or already registered in this namespace
     */
    void register(String key, PlaceholderResolver resolver);

    /** Registers every {@code @Placeholder} method of {@code handler}. */
    void register(Object handler);

    /** The keys registered in this namespace. */
    Set<String> keys();

    /** Drops every placeholder this manager registered. */
    void unregisterAll();

    /**
     * Fills in every placeholder in {@code text} - from any namespace, not
     * just this one - for {@code player} (platform type, or {@code null}).
     */
    String apply(Object player, String text);

    /**
     * {@link #apply(Object, String)}, with {@code context} passed to every
     * resolver - {@code @Context} parameters and {@link PlaceholderContext#text()}.
     * CoffeeLib's own managers implement it; the default is for implementations
     * from elsewhere.
     */
    default String apply(Object player, String text, TextContext context) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support text contexts");
    }
}
