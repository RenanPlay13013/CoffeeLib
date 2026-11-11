package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Paper platform. */
public final class CoffeeLib {

    private CoffeeLib() {
    }

    /**
     * Creates a {@link ReloadableConfigManager} bound to {@code plugin}'s own
     * data folder. Each plugin gets its own manager instance — nothing here is
     * shared across plugins, even within the same server process.
     * <p>
     * {@link ConfigFormat#NATIVE} means YAML on this platform, so an
     * unannotated {@code @ConfigFile} behaves exactly as before; a class can
     * opt into JSON or HOCON per {@code @ConfigFile(format = ...)}. Every
     * config loaded here is one CoffeeLib manages itself, which is what makes
     * manual reload and hot reload available on the returned manager.
     */
    public static ReloadableConfigManager forPlugin(JavaPlugin plugin) {
        return new PaperConfigManager(plugin.getName(), plugin.getDataFolder().toPath());
    }
}
