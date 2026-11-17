package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.core.event.SharedEventBus;
import net.loyalnetwork.coffeelib.database.OpenDatabases;
import net.loyalnetwork.coffeelib.paper.placeholder.PaperPlaceholderManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

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

    /** Databases of disabled plugins, waiting for the next tick to be closed. */
    private final Map<List<Database>, String> pendingClose = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * A handler holds the plugin object that registered it, and through it that
     * plugin's class loader. Paper discards the loader when a plugin is
     * disabled, so a handler left behind would both keep the old classes alive
     * and keep answering events on behalf of a plugin that is gone - the usual
     * way a /reload turns into duplicated or impossible behavior. Clearing them
     * here means a plugin gets this right without having to remember to.
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        SharedEventBus.unregisterAll(event.getPlugin().getName());
        // Same reasoning for placeholders: a resolver holds the plugin's objects, and a
        // published PlaceholderAPI expansion would keep answering for a plugin that is gone.
        PaperPlaceholderManager.cleanUp(event.getPlugin().getName());

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
        pendingClose.forEach((databases, owner) -> OpenDatabases.close(owner, databases));
        pendingClose.clear();
        OpenDatabases.closeAll();
    }
}
