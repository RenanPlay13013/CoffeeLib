package net.loyalnetwork.coffeelib.core.placeholder;

import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;

import java.util.Locale;
import java.util.Set;

/**
 * The one placeholder registry every owner shares, which is the whole point:
 * on Forge and NeoForge nothing else lets one mod resolve another's
 * placeholders.
 * <p>
 * Same caveat as the shared event bus: it only spans owners that load the
 * same CoffeeLib classes, i.e. CoffeeLib installed as its own mod or plugin.
 */
public final class SharedPlaceholders {

    private static final PlaceholderRegistry REGISTRY = new PlaceholderRegistry();

    private SharedPlaceholders() {
    }

    public static PlaceholderRegistry registry() {
        return REGISTRY;
    }

    /** A manager registering under {@code namespace} on behalf of {@code ownerId}. */
    public static PlaceholderManager forOwner(String ownerId, String namespace) {
        return new DefaultPlaceholderManager(REGISTRY, ownerId, normalize(namespace), null);
    }

    /** Fills in every placeholder in {@code text} for {@code player} (platform type, or {@code null}). */
    public static String apply(Object player, String text) {
        return REGISTRY.apply(player, text);
    }

    public static Set<String> namespaces() {
        return REGISTRY.namespaces();
    }

    /** Mod ids and plugin names as a namespace: lower case, spaces as {@code -}. */
    public static String normalize(String namespace) {
        return namespace.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
