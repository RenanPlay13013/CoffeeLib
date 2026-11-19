package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.text.Messages;
import net.loyalnetwork.coffeelib.api.text.TextFormatter;
import net.loyalnetwork.coffeelib.core.text.MessageCatalogs;
import net.loyalnetwork.coffeelib.paper.text.PaperTextFormatter;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.database.DatabaseManager;
import net.loyalnetwork.coffeelib.api.economy.EconomyManager;
import net.loyalnetwork.coffeelib.api.permission.PermissionManager;
import net.loyalnetwork.coffeelib.api.player.PlayerDirectory;
import net.loyalnetwork.coffeelib.api.scheduler.Scheduler;
import net.loyalnetwork.coffeelib.api.di.ServiceContainer;
import net.loyalnetwork.coffeelib.core.di.SharedServices;
import net.loyalnetwork.coffeelib.core.di.PlatformServices;
import net.loyalnetwork.coffeelib.core.player.SharedPlayers;
import net.loyalnetwork.coffeelib.paper.scheduler.PaperScheduler;
import net.loyalnetwork.coffeelib.core.economy.SharedEconomy;
import net.loyalnetwork.coffeelib.database.DefaultDatabaseManager;
import net.loyalnetwork.coffeelib.paper.permission.PaperPermissionManager;
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
        services(plugin);
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
     * Permission nodes for {@code plugin}. Checks go through Bukkit, which
     * LuckPerms and other permission plugins already hook into; declaring a
     * node registers it with the server with its default, so it shows up in
     * permission plugins and ops get it with no plugin at all.
     */
    public static PermissionManager permissions(JavaPlugin plugin) {
        return new PaperPermissionManager(plugin);
    }

    /**
     * Databases for {@code plugin}: {@code open()} connects with the plugin's
     * {@code database.yml} (in its data folder), {@code open(settings)} with
     * settings of its own. Opened databases are closed when the plugin is
     * disabled, after its {@code onDisable} has run.
     */
    public static DatabaseManager database(JavaPlugin plugin) {
        return database(plugin, null);
    }

    /**
     * {@link #database(JavaPlugin)}, with {@code database.yml} in
     * {@code configDirectory} instead - the same choice
     * {@link #forPlugin(JavaPlugin, Path)} offers for configs.
     *
     * @param configDirectory where {@code database.yml} lives, or {@code null}
     *                        for the plugin's data folder
     */
    public static DatabaseManager database(JavaPlugin plugin, Path configDirectory) {
        return new DefaultDatabaseManager(plugin.getName(), () -> forPlugin(plugin, configDirectory),
                plugin.getClass().getClassLoader(),
                () -> configDirectory != null ? configDirectory : plugin.getDataFolder().toPath());
    }

    /**
     * The economy channel for {@code plugin}: register the currencies it provides,
     * and take handles to anyone's ({@code currency("coins")}). CoffeeLib holds
     * no balances; the providing mod answers every call.
     */
    public static EconomyManager economy(JavaPlugin plugin) {
        return SharedEconomy.forOwner(plugin.getName());
    }

    /**
     * Work on the main thread for {@code plugin}, through Bukkit's scheduler:
     * now, after some ticks, or every so many, plus {@code main()} to bring a
     * future's result back to the main thread. Bukkit cancels the tasks when
     * the plugin is disabled.
     */
    public static Scheduler scheduler(JavaPlugin plugin) {
        return new PaperScheduler(plugin);
    }

    /**
     * Who can see whom, shared by every mod on CoffeeLib: a vanish mod
     * registers its rule here, and every CoffeeLib command taking a player
     * leaves hidden players out of completion and reads them as not found.
     */
    public static PlayerDirectory players() {
        return SharedPlayers.directory();
    }

    /** Persistent player values using native PDC on Bukkit 1.14+. Fails explicitly on older servers. */
    public static net.loyalnetwork.coffeelib.api.player.PlayerDataFactory playerData(JavaPlugin plugin) {
        return net.loyalnetwork.coffeelib.paper.player.PaperPlayerDataFactory.forPlugin(plugin);
    }

    /** One service registry per plugin; shared by its command and event handlers. */
    public static ServiceContainer services(JavaPlugin plugin) {
        return SharedServices.forOwner(plugin.getName(), () -> new PlatformServices(
                permissions(plugin), scheduler(plugin), database(plugin), players()));
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
        services(plugin);
        return SharedEventBus.forOwner(plugin.getName());
    }
    /** Legacy-compatible text formatter, including PlaceholderAPI when installed. */
    public static TextFormatter<String> text(JavaPlugin plugin) {
        return new PaperTextFormatter(placeholders(plugin));
    }

    /** Loads a reloadable message catalog in the plugin's own data directory. */
    public static <T> Messages<T> messages(JavaPlugin plugin, Class<T> catalogType) {
        TextFormatter<String> formatter = text(plugin);
        Scheduler main = scheduler(plugin);
        return MessageCatalogs.open(plugin.getName(), plugin.getDataFolder().toPath(), catalogType,
                (recipient, message) -> main.onMain(() -> {
                    if (!plugin.isEnabled()) return;
                    Object nativeRecipient = recipient instanceof CommandSource source ? source.unwrap() : recipient;
                    if (!(nativeRecipient instanceof CommandSender sender)) {
                        throw new IllegalArgumentException("Unsupported Paper message recipient: " + nativeRecipient.getClass());
                    }
                    if (sender instanceof Player player && !player.isOnline()) return;
                    sender.sendMessage(formatter.parse(message, sender instanceof Player ? sender : null));
                }));
    }
}
