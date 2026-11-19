package net.loyalnetwork.coffeelib.neoforge;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.ReloadableConfigManager;
import net.loyalnetwork.coffeelib.api.command.CommandManager;
import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.database.DatabaseManager;
import net.loyalnetwork.coffeelib.api.economy.EconomyManager;
import net.loyalnetwork.coffeelib.api.permission.PermissionManager;
import net.loyalnetwork.coffeelib.core.economy.SharedEconomy;
import net.loyalnetwork.coffeelib.database.DefaultDatabaseManager;
import net.loyalnetwork.coffeelib.core.permission.DefaultPermissionManager;
import net.loyalnetwork.coffeelib.neoforge.permission.NeoForgePermissions;
import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;
import net.loyalnetwork.coffeelib.core.placeholder.SharedPlaceholders;
import net.loyalnetwork.coffeelib.neoforge.commands.NeoForgeCommandManager;
import net.loyalnetwork.coffeelib.core.event.SharedEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.function.Supplier;

/** Entry point for the NeoForge platform. */
public final class CoffeeLib {

    private CoffeeLib() {
    }

    /**
     * Creates a {@link ReloadableConfigManager} bound to {@code container}'s
     * mod. Call this from the mod's constructor, matching NeoForge's own
     * convention for {@code ModContainer#registerConfig}.
     * <p>
     * What {@link ReloadableConfigManager#load} then does depends on the
     * config class:
     * <ul>
     *   <li>{@code @ConfigFile("x")} (i.e. {@link ConfigFormat#NATIVE}) —
     *       registers with NeoForge's config system immediately, but the
     *       fields only reflect what is on disk once NeoForge fires
     *       {@code ModConfigEvent.Loading}, shortly after construction rather
     *       than synchronously during the call.</li>
     *   <li>{@code @ConfigFile(value = "x", format = YAML)} and the other
     *       CoffeeLib-managed formats — the file in {@code config/} is read,
     *       validated and written back during the call, and the instance you
     *       get is already populated. Nothing about it is tied to mod
     *       loading, and it can be reloaded at any time through this same
     *       manager or {@code /coffeelib config}.</li>
     * </ul>
     */
    public static ReloadableConfigManager forMod(ModContainer container) {
        return forMod(container, null);
    }

    /**
     * {@link #forMod(ModContainer)}, with CoffeeLib-managed configs going to
     * {@code configDirectory} instead of the loader's {@code config/}.
     * <p>
     * For a mod that wants its files somewhere of its own —
     * {@code FMLPaths.GAMEDIR.get().resolve("mymod")}, say. The directory is
     * created on first write, and a relative path resolves against the process
     * working directory, which for a server is the game directory.
     * {@link ConfigFormat#NATIVE} configs are unaffected: the loader owns
     * those, and it always writes them under {@code config/}.
     *
     * @param configDirectory where managed configs live, or {@code null} for
     *                        the loader's own {@code config/}
     */
    public static ReloadableConfigManager forMod(ModContainer container, Path configDirectory) {
        return new NeoForgeConfigManager(container.getModId(), container, container.getEventBus(),
                directorySupplier(configDirectory));
    }

    /**
     * Creates a manager for CoffeeLib-managed formats only, callable at any
     * point in a mod's life — not just from its constructor.
     * <p>
     * Use it when you want configs that have nothing to do with the
     * mod-loading lifecycle: loading one reads the file there and then, so a
     * manager built in a server-start handler, a command, or a reload hook
     * works exactly the same as one built during construction. Asking it for
     * a {@link ConfigFormat#NATIVE} config fails with a
     * {@code ConfigException}, since registering a {@code ModConfig} is only
     * legal while the mod is being constructed.
     *
     * @param modId the mod this manager belongs to — also the key an operator
     *              types in {@code /coffeelib config reload <mod>}
     */
    public static ReloadableConfigManager detached(String modId) {
        return detached(modId, null);
    }

    /**
     * {@link #detached(String)}, with managed configs going to
     * {@code configDirectory} instead of the loader's {@code config/}.
     *
     * @param configDirectory where managed configs live, or {@code null} for
     *                        the loader's own {@code config/}
     */
    public static ReloadableConfigManager detached(String modId, Path configDirectory) {
        return new NeoForgeConfigManager(modId, null, null, directorySupplier(configDirectory));
    }

    /**
     * Keeps the caller's directory lazy the same way the default one is, so
     * neither is resolved until a managed config is actually loaded.
     */
    private static Supplier<Path> directorySupplier(Path configDirectory) {
        return configDirectory != null ? () -> configDirectory : CoffeeLib::configDirectory;
    }

    /**
     * The CoffeeLib event bus, as a handle bound to the calling mod. Call it
     * from the mod's constructor, where the {@code ModContainer} gives the owner
     * id; {@link #eventBus(String)} works anywhere.
     * <p>
     * This is CoffeeLib's own bus, not NeoForge's: handlers registered here
     * see events posted by any other mod using the library, on any platform,
     * with no NeoForge event classes involved. NeoForge's own
     * {@code IEventBus} is still the right place for Minecraft's events.
     * <p>
     * Cross-mod delivery needs every mod to load the same CoffeeLib classes,
     * which is what installing this jar as a mod gives you. A mod that shades
     * CoffeeLib into itself gets a private bus instead - see
     * {@link net.loyalnetwork.coffeelib.core.event.SharedEventBus}.
     */
    /**
     * Annotation-driven commands for {@code container}'s mod: every
     * {@code @Command} method of a handler passed to
     * {@link CommandManager#register} becomes a Brigadier command with tab
     * completion. Call it from the mod constructor or later - commands
     * registered while a server runs are added to it straight away.
     */
    public static CommandManager commands(ModContainer container) {
        return commands(container.getModId());
    }

    /** {@link #commands(ModContainer)} for when only the mod id is at hand. */
    public static CommandManager commands(String modId) {
        return new NeoForgeCommandManager(modId);
    }

    /**
     * Placeholders for {@code container}'s mod, under its mod id, registered as {@code %<namespace>_<key>%} in the
     * registry every mod on CoffeeLib shares - the intermediary NeoForge does not
     * have. {@link PlaceholderManager#apply} fills in any mod's placeholders,
     * not just your own.
     */
    public static PlaceholderManager placeholders(ModContainer container) {
        return placeholders(container.getModId(), container.getModId());
    }

    /** Placeholders for {@code modId} under a namespace of its own choosing, e.g. {@code "crates"}. */
    public static PlaceholderManager placeholders(String modId, String namespace) {
        return SharedPlaceholders.forOwner(modId, namespace);
    }

    /**
     * Permission nodes for {@code container}'s mod: declared with a default, checked
     * through LuckPerms when it is installed and against that default when it
     * is not (or has no answer).
     */
    public static PermissionManager permissions(ModContainer container) {
        return permissions(container.getModId());
    }

    /** {@link PermissionManager} for {@code modId}. */
    public static PermissionManager permissions(String modId) {
        return new DefaultPermissionManager(modId, NeoForgePermissions.resolver());
    }

    /**
     * Databases for {@code container}'s mod: {@code open()} connects with
     * {@code config/<modid>/database.yml}, {@code open(settings)} with
     * settings of its own. Opened databases are closed when the game exits.
     */
    public static DatabaseManager database(ModContainer container) {
        return database(container.getModId(), null);
    }

    /** {@link #database(ModContainer)}, with {@code database.yml} in {@code configDirectory} ({@code null}: the default). */
    public static DatabaseManager database(ModContainer container, Path configDirectory) {
        return database(container.getModId(), configDirectory);
    }

    /** {@link DatabaseManager} for {@code modId}. */
    public static DatabaseManager database(String modId) {
        return database(modId, null);
    }

    /**
     * {@link #database(String)}, with {@code database.yml} in
     * {@code configDirectory} instead - the same choice
     * {@link #detached(String, Path)} offers for configs.
     *
     * @param configDirectory where {@code database.yml} lives, or {@code null}
     *                        for {@code config/<modid>/}
     */
    public static DatabaseManager database(String modId, Path configDirectory) {
        // By default database.yml goes in config/<modid>/: every mod has one, so the
        // loader's own config/ would have them all overwrite each other. Every mod's
        // classes are visible from the loader that loaded CoffeeLib's own.
        Supplier<Path> directory = () -> configDirectory != null ? configDirectory
                : FMLPaths.CONFIGDIR.get().resolve(modId);
        return new DefaultDatabaseManager(modId, () -> detached(modId, directory.get()),
                CoffeeLib.class.getClassLoader(), directory);
    }

    /**
     * The economy channel for {@code container's mod}: register the currencies it provides,
     * and take handles to anyone's ({@code currency("coins")}). CoffeeLib holds
     * no balances; the providing mod answers every call.
     */
    public static EconomyManager economy(ModContainer container) {
        return economy(container.getModId());
    }

    /** {@link EconomyManager} for {@code modId}. */
    public static EconomyManager economy(String modId) {
        return SharedEconomy.forOwner(modId);
    }

    public static EventBus eventBus(ModContainer container) {
        return eventBus(container.getModId());
    }

    /**
     * The event bus as a handle bound to {@code modId}, callable at any point
     * in a mod's life.
     *
     * @param modId the mod this handle belongs to - the id its handlers are
     *              recorded against, and the one {@code unregisterAll()} clears
     */
    public static EventBus eventBus(String modId) {
        return SharedEventBus.forOwner(modId);
    }

    /**
     * NeoForge's {@code config/} directory, the same one a native
     * {@code ModConfig} writes its TOML into — so a mod's managed files sit
     * next to the files an operator already expects to find there.
     * <p>
     * Passed around as a supplier, not a resolved path: FML only fills these
     * in once the loader is running, and a mod using native configs
     * exclusively should never make CoffeeLib ask.
     */
    private static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
