package net.loyalnetwork.coffeelib.api.text;

/**
 * Platform-owned rendering of Minecraft legacy formatting into the platform's
 * native text type. The core does not depend on Minecraft classes.
 * <p>Accepted formatting: {@code &0..&f}, {@code &k..&o}, {@code &r},
 * {@code &#RRGGBB}, their {@code §} equivalents, and escaped {@code \&}.
 * Unknown codes remain ordinary text. Implementations must document their
 * supported color depth. Null inputs are rejected.
 * <p>Rendering does not access the game world; callers must still use the
 * platform's thread when they send the returned component to a player.
 */
public interface TextFormatter<C> {
    C parse(String text);

    /** Resolves registered placeholders for a player, then parses styling. */
    C parse(String text, Object player);

    /**
     * {@link #parse(String, Object)}, with {@code context} given to every
     * placeholder resolver (see {@code TextContext}). CoffeeLib's formatters
     * implement it; the default is for implementations from elsewhere.
     */
    default C parse(String text, Object player, net.loyalnetwork.coffeelib.api.placeholder.TextContext context) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support text contexts");
    }

    /** Removes recognized style codes without removing ordinary ampersands. */
    String strip(String text);
}
