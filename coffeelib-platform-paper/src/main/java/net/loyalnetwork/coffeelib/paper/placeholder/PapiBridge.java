package net.loyalnetwork.coffeelib.paper.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.loyalnetwork.coffeelib.core.placeholder.SharedPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Everything that touches PlaceholderAPI, in one class nothing loads unless
 * the PlaceholderAPI plugin is present - so CoffeeLib runs the same with or
 * without it, and never bundles it.
 * <p>
 * Each CoffeeLib namespace is published as a {@link PlaceholderExpansion}, so
 * any plugin that already uses PlaceholderAPI sees a CoffeeLib owner's
 * placeholders with no change on its side.
 */
final class PapiBridge {

    private static final Logger LOGGER = Logger.getLogger(PapiBridge.class.getName());

    /** Published expansions, by namespace. */
    private static final Map<String, Expansion> PUBLISHED = new ConcurrentHashMap<>();

    private PapiBridge() {
    }

    /** Whether the PlaceholderAPI plugin is installed and enabled - checked without touching its classes. */
    static boolean available() {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        return papi != null && papi.isEnabled();
    }

    /** Publishes {@code namespace} as an expansion once; later keys in it need nothing more. */
    static void publish(String namespace, Plugin owner) {
        if (namespace.indexOf('_') >= 0) {
            // PlaceholderAPI cuts the identifier at the first '_', so it could never route these.
            LOGGER.warning("[CoffeeLib/" + owner.getName() + "] Placeholder namespace '" + namespace
                    + "' contains '_': it resolves through CoffeeLib, but PlaceholderAPI cannot route it "
                    + "for other plugins. Pick a namespace without '_' to publish it there.");
            return;
        }
        PUBLISHED.computeIfAbsent(namespace, ns -> {
            Expansion expansion = new Expansion(ns, owner);
            expansion.register();
            return expansion;
        });
    }

    /** Withdraws every expansion published on behalf of {@code owner}. */
    static void unpublishOwner(String ownerName) {
        PUBLISHED.values().removeIf(expansion -> {
            if (expansion.owner.getName().equals(ownerName)) {
                expansion.unregister();
                return true;
            }
            return false;
        });
    }

    /** Fills in every PlaceholderAPI placeholder - other plugins' included - for {@code player}. */
    static String apply(Object player, String text) {
        OfflinePlayer offline = player instanceof OfflinePlayer offlinePlayer ? offlinePlayer : null;
        return PlaceholderAPI.setPlaceholders(offline, text);
    }

    /** One CoffeeLib namespace, answered from the shared registry. */
    private static final class Expansion extends PlaceholderExpansion {

        private final String namespace;
        private final Plugin owner;

        private Expansion(String namespace, Plugin owner) {
            this.namespace = namespace;
            this.owner = owner;
        }

        @Override
        public String getIdentifier() {
            return namespace;
        }

        @Override
        public String getAuthor() {
            List<String> authors = owner.getDescription().getAuthors();
            return authors.isEmpty() ? owner.getName() : String.join(", ", authors);
        }

        @Override
        public String getVersion() {
            return owner.getDescription().getVersion();
        }

        /** Survive {@code /papi reload}; the owner withdraws it when it is disabled instead. */
        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            // Resolvers that declare Player expect an online one; an OfflinePlayer resolver
            // still matches, since Player is an OfflinePlayer.
            Object resolved = player != null && player.isOnline() ? player.getPlayer() : player;
            return SharedPlaceholders.registry().resolve(namespace, params, resolved);
        }
    }
}
