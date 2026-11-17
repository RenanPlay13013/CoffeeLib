package net.loyalnetwork.coffeelib.core.placeholder;

import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderResolver;

import java.util.Map;
import java.util.Set;

/**
 * One owner's handle onto a {@link PlaceholderRegistry}: registers under its
 * namespace, applies across all of them.
 * <p>
 * Platforms that need to know when a namespace first gets a placeholder -
 * Paper, to publish it to PlaceholderAPI - pass a {@link Listener}.
 */
public class DefaultPlaceholderManager implements PlaceholderManager {

    /** Told about registrations, after the registry accepted them. */
    public interface Listener {
        void registered(String namespace, String key);
    }

    private final PlaceholderRegistry registry;
    private final String ownerId;
    private final String namespace;
    private final Listener listener;

    public DefaultPlaceholderManager(PlaceholderRegistry registry, String ownerId, String namespace,
                                     Listener listener) {
        this.registry = registry;
        this.ownerId = ownerId;
        this.namespace = namespace;
        this.listener = listener;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public void register(String key, PlaceholderResolver resolver) {
        registry.register(ownerId, namespace, key, resolver);
        if (listener != null) {
            listener.registered(namespace, key);
        }
    }

    @Override
    public void register(Object handler) {
        // Scanned in full before anything is registered, so a bad method leaves nothing half-done.
        Map<String, PlaceholderResolver> resolvers = PlaceholderScanner.scan(handler);
        for (Map.Entry<String, PlaceholderResolver> entry : resolvers.entrySet()) {
            register(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Set<String> keys() {
        return registry.keys(namespace);
    }

    @Override
    public void unregisterAll() {
        registry.unregisterAll(ownerId, namespace);
    }

    @Override
    public String apply(Object player, String text) {
        return registry.apply(player, text);
    }
}
