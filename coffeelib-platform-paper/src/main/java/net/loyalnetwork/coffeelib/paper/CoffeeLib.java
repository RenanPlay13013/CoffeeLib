package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.ConfigManager;
import net.loyalnetwork.coffeelib.core.DefaultConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Paper platform. */
public final class CoffeeLib {

    private CoffeeLib() {
    }

    /**
     * Creates a {@link ConfigManager} bound to {@code plugin}'s own data
     * folder. Each plugin gets its own manager instance — nothing here is
     * shared across plugins, even within the same server process.
     */
    public static ConfigManager forPlugin(JavaPlugin plugin) {
        return new DefaultConfigManager(plugin.getDataFolder().toPath(), new YamlConfigBackend());
    }
}
