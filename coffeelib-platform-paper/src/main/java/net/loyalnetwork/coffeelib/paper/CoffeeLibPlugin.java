package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.core.economy.SharedEconomy;
import net.loyalnetwork.coffeelib.core.command.CommandRoots;
import net.loyalnetwork.coffeelib.core.player.SharedPlayers;
import net.loyalnetwork.coffeelib.core.event.SharedEventBus;
import net.loyalnetwork.coffeelib.core.di.SharedServices;
import net.loyalnetwork.coffeelib.database.OpenDatabases;
import net.loyalnetwork.coffeelib.paper.placeholder.PaperPlaceholderManager;
import net.loyalnetwork.coffeelib.paper.scheduler.PaperScheduler;
import net.loyalnetwork.coffeelib.paper.permission.PaperPermissionEvents;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes Paper accept this jar as a plugin, and runs the one piece of behavior
 * CoffeeLib needs for itself: dropping a plugin's event-bus handlers when that
 * plugin is disabled.
 * <p>
 * Everything else here is library code other plugins call into via
 * {@link CoffeeLib#forPlugin} and {@link CoffeeLib#eventBus}.
 */
public final class CoffeeLibPlugin extends JavaPlugin implements Listener {

    private PaperPermissionEvents permissionEvents;

    /** Databases of disabled plugins, waiting for the next tick to be closed. */
    private final Map<List<Database>, String> pendingClose = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        permissionEvents = new PaperPermissionEvents(this);
        permissionEvents.start();
        SharedPlayers.configureOnlinePlayers(Bukkit::getOnlinePlayers,
                (viewer, target) -> !(viewer instanceof Player player && target instanceof Player other)
                        || player == other || player.canSee(other));
        // The economy bridge names Vault's classes, so it is only loaded when Vault is there.
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            net.loyalnetwork.coffeelib.paper.economy.VaultBridge.install(this,
                    CoffeeLib.forPlugin(this).load(net.loyalnetwork.coffeelib.paper.economy.EconomyConfig.class));
        }
    }

    /**
     * A handler holds the plugin object that registered it, and through it that
     * plugin's class loader. Paper discards the loader when a plugin is
     * disabled, so a handler left behind would both keep the old classes alive
     * and keep answering events on behalf of a plugin that is gone - the usual
     * way a /reload turns into duplicated or impossible behavior. Clearing them
     * here means a plugin gets this right without having to remember to.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        net.loyalnetwork.coffeelib.paper.player.PaperPlayerDataFactory.onDeath(event.getEntity());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        net.loyalnetwork.coffeelib.paper.menu.PaperMenus.releaseOwner(event.getPlugin());
        PaperScheduler.cancelOwner(event.getPlugin());
        net.loyalnetwork.coffeelib.paper.player.PaperPlayerDataFactory.releaseOwner(event.getPlugin().getName());
        SharedPermissionChanges.releaseOwner(event.getPlugin().getName());
        CommandRoots.releaseOwner(event.getPlugin().getName());
        SharedPlayers.releaseOwner(event.getPlugin().getName());
        SharedEventBus.unregisterAll(event.getPlugin().getName());
        SharedServices.releaseOwner(event.getPlugin().getName());
        // Same reasoning for placeholders: a resolver holds the plugin's objects, and a
        // published PlaceholderAPI expansion would keep answering for a plugin that is gone.
        PaperPlaceholderManager.cleanUp(event.getPlugin().getName());
        // And its currencies: a provider answering for a disabled plugin would serve stale or
        // unreachable balances, and keep the old classes alive.
        SharedEconomy.cleanUp(event.getPlugin().getName());

        // Databases too, but not yet: Bukkit fires this event before the plugin's own
        // onDisable, which is exactly where a plugin saves its data. So they are taken now -
        // a new instance enabled by a /reload gets databases of its own - and closed on the
        // next tick, once onDisable is over. At shutdown there is no next tick; CoffeeLib's
        // own onDisable, which runs after every plugin depending on it, closes them instead.
        String owner = event.getPlugin().getName();
        if (event.getPlugin() == this) {
            return;
        }
        List<Database> databases = OpenDatabases.detach(owner);
        if (!databases.isEmpty()) {
            pendingClose.put(databases, owner);
            getServer().getScheduler().runTask(this, () -> {
                if (pendingClose.remove(databases) != null) {
                    OpenDatabases.close(owner, databases);
                }
            });
        }
    }

    @Override
    public void onDisable() {
        if (permissionEvents != null) permissionEvents.stop();
        net.loyalnetwork.coffeelib.paper.menu.PaperMenus.releaseOwner(this);
        PaperScheduler.cancelOwner(this);
        CommandRoots.releaseOwner(getName());
        SharedPlayers.clearAll();
        net.loyalnetwork.coffeelib.paper.player.PaperPlayerDataFactory.clearAll();
        SharedServices.clearAll();
        pendingClose.forEach((databases, owner) -> OpenDatabases.close(owner, databases));
        pendingClose.clear();
        OpenDatabases.closeAll();
    }
}
