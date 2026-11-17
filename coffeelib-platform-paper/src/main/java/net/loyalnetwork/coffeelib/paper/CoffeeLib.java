package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;
import net.loyalnetwork.coffeelib.paper.placeholder.PaperPlaceholderManager;
import net.loyalnetwork.coffeelib.paper.commands.PaperCommandManager;
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
     * Annotation-driven commands for {@code plugin}: every {@code @Command}
     * method of a handler passed to {@link CommandManager#register} becomes a
     * Bukkit command with tab completion, under {@code plugin}'s own namespace
     * ({@code /yourplugin:crate}). No {@code plugin.yml} entry is needed.
     */
    public static CommandManager commands(JavaPlugin plugin) {
        return new PaperCommandManager(plugin);
    }

    /**
     * Placeholders for {@code plugin}, under its own name as the namespace
     * ({@code %yourplugin_key%}). With PlaceholderAPI installed, the namespace
     * is published there too, and {@link PlaceholderManager#apply} also fills
     * in every other plugin's PlaceholderAPI placeholders. Dropped automatically
     * when the plugin is disabled.
     */
    public static PlaceholderManager placeholders(JavaPlugin plugin) {
        return placeholders(plugin, plugin.getName());
    }

    /**
     * {@link #placeholders(JavaPlugin)} under a namespace of your choosing.
     * Avoid {@code _} in it if other plugins should see it through
     * PlaceholderAPI, which cuts identifiers at the first underscore.
     */
    public static PlaceholderManager placeholders(JavaPlugin plugin, String namespace) {
        return new PaperPlaceholderManager(plugin, namespace);
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
