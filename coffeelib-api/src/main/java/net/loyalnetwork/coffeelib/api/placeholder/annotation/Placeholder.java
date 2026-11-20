package net.loyalnetwork.coffeelib.api.placeholder.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the resolver for {@code %namespace_<value>%}.
 * <pre>{@code
 * public final class CratePlaceholders {
 *
 *     @Placeholder("keys")              // %crates_keys_daily% -> argument "daily"
 *     int keys(ServerPlayer player, String crate) { ... }
 *
 *     @Placeholder("total")             // %crates_total%, needs no player
 *     long total() { ... }
 * }
 * }</pre>
 * Parameters, in any order and all optional: the player in the platform's own
 * type (if the text has no player, or a different kind of one, the placeholder
 * is left unresolved instead of calling the method), a {@code String} for the
 * argument, a {@code PlaceholderContext} for everything, and any number of
 * {@link Context @Context} parameters for what the text is about. Any return type is
 * turned into text with {@code toString()}; {@code null} leaves the
 * placeholder unresolved.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Placeholder {

    /** The key: lower case letters, digits, {@code _} and {@code -}. */
    String value();
}
