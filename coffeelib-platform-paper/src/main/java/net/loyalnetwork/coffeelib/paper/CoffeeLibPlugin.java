package net.loyalnetwork.coffeelib.paper;

import net.loyalnetwork.coffeelib.core.event.SharedEventBus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Makes Paper accept this jar as a plugin, and runs the one piece of behavior
 * CoffeeLib needs for itself: dropping a plugin's event-bus handlers when that
 * plugin is disabled.
 * <p>
 * Everything else here is library code other plugins call into via
 * {@link CoffeeLib#forPlugin} and {@link CoffeeLib#eventBus}.
 */
public final class CoffeeLibPlugin extends JavaPlugin implements Listener {

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
    }
}
