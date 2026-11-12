package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.core.event.SharedEventBus;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

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
        return forPlugin(plugin, null);
    }

    /**
     * {@link #forPlugin(JavaPlugin)}, with configs going to
     * {@code configDirectory} instead of the plugin's data folder — a folder
     * shared between a group of plugins, say. The directory is created on
     * first write.
     *
     * @param configDirectory where configs live, or {@code null} for
     *                        {@code plugin}'s own data folder
     */
    public static ReloadableConfigManager forPlugin(JavaPlugin plugin, Path configDirectory) {
        Path directory = configDirectory != null ? configDirectory : plugin.getDataFolder().toPath();
        return new PaperConfigManager(plugin.getName(), directory);
    }

    /**
     * The CoffeeLib event bus, as a handle bound to {@code plugin}.
     * <p>
     * This is CoffeeLib's own bus, not Bukkit's: handlers registered here see
     * events posted by any other plugin - or Forge/NeoForge mod - using the
     * library, as plain Java types, with no {@code org.bukkit.event} classes
     * and no {@code PluginManager} registration involved. Bukkit's own event
     * system is still the right place for the server's events.
     * <p>
     * Your plugin's handlers are dropped automatically when it is disabled, so
     * a reload cannot leave the old copy of your classes answering events.
     * Cross-plugin delivery needs your plugin to resolve CoffeeLib's classes
     * from the installed CoffeeLib plugin, which means declaring it under
     * {@code depend} or {@code softdepend} in your {@code plugin.yml}; shading
     * CoffeeLib into your own jar gives you a private bus instead - see
     * {@link SharedEventBus}.
     */
    public static EventBus eventBus(JavaPlugin plugin) {
        return SharedEventBus.forOwner(plugin.getName());
    }
}
