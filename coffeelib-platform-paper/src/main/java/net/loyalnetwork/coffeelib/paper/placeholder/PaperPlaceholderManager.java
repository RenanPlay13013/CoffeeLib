package net.loyalnetwork.coffeelib.paper.placeholder;

import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderResolver;
import net.loyalnetwork.coffeelib.core.placeholder.DefaultPlaceholderManager;
import net.loyalnetwork.coffeelib.core.placeholder.SharedPlaceholders;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Paper's {@link PlaceholderManager}: CoffeeLib's shared registry, plus a
 * bridge to PlaceholderAPI when it is installed.
 * <p>
 * With PlaceholderAPI, registering also publishes the namespace there, and
 * {@link #apply} resolves CoffeeLib's placeholders first and then hands the
 * text to PlaceholderAPI for everyone else's. CoffeeLib's go first so a
 * namespace with {@code _}, which PlaceholderAPI cannot route, still resolves.
 * Without PlaceholderAPI it is just the shared registry, the same as on
 * Forge and NeoForge.
 */
public final class PaperPlaceholderManager implements PlaceholderManager {

    private final JavaPlugin plugin;
    private final DefaultPlaceholderManager delegate;

    public PaperPlaceholderManager(JavaPlugin plugin, String namespace) {
        // The platform's player types, so a @Context Player is refused at registration.
        SharedPlaceholders.playerTypes(org.bukkit.OfflinePlayer.class);
        this.plugin = plugin;
        this.delegate = new DefaultPlaceholderManager(SharedPlaceholders.registry(), plugin.getName(),
                SharedPlaceholders.normalize(namespace), (ns, key) -> {
                    if (PapiBridge.available()) {
                        PapiBridge.publish(ns, plugin);
                    }
                });
    }

    @Override
    public String namespace() {
        return delegate.namespace();
    }

    @Override
    public void register(String key, PlaceholderResolver resolver) {
        delegate.register(key, resolver);
    }

    @Override
    public void register(Object handler) {
        delegate.register(handler);
    }

    @Override
    public Set<String> keys() {
        return delegate.keys();
    }

    @Override
    public void unregisterAll() {
        delegate.unregisterAll();
        if (PapiBridge.available()) {
            PapiBridge.unpublishOwner(plugin.getName());
        }
    }

    @Override
    public String apply(Object player, String text) {
        return apply(player, text, net.loyalnetwork.coffeelib.api.placeholder.TextContext.EMPTY);
    }

    /**
     * CoffeeLib's placeholders first, with {@code context}; then PlaceholderAPI
     * for what is left - which has no context, so it only ever sees the
     * text's player.
     */
    @Override
    public String apply(Object player, String text, net.loyalnetwork.coffeelib.api.placeholder.TextContext context) {
        String ours = delegate.apply(player, text, context);
        return PapiBridge.available() ? PapiBridge.apply(player, ours) : ours;
    }

    /**
     * Called by the CoffeeLib plugin when any plugin is disabled: drops its
     * placeholders and withdraws its expansions, so a reload cannot leave the
     * old copy of its classes answering.
     */
    public static void cleanUp(String pluginName) {
        SharedPlaceholders.registry().unregisterOwner(pluginName);
        if (PapiBridge.available()) {
            PapiBridge.unpublishOwner(pluginName);
        }
    }
}
