package net.loyalnetwork.coffeelib.forge.permission;

import net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges;
import net.minecraftforge.fml.ModList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Optional LuckPerms subscription, retried until its provider has initialized. */
public final class ForgePermissionEvents {
    private static final Logger LOG = Logger.getLogger(ForgePermissionEvents.class.getName());
    private static Runnable close;
    private static int ticks;
    private static boolean loggedIncompatible;

    private ForgePermissionEvents() { }

    /** Called from the server tick (on the main thread). */
    public static void onTick() {
        if (close != null || ++ticks % 20 != 0) return;
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("luckperms")) return;
        try {
            close = LuckPermsHook.subscribe();
        } catch (IllegalStateException notReady) {
            // LuckPermsProvider is not registered yet. Retry in a second.
        } catch (LinkageError incompatible) {
            if (!loggedIncompatible) {
                loggedIncompatible = true;
                LOG.log(Level.WARNING, "LuckPerms event API could not be loaded", incompatible);
            }
        }
    }

    /** On integrated-server shutdown, do not keep a subscription across world instances. */
    public static void onStop() {
        Runnable previous = close;
        close = null;
        ticks = 0;
        if (previous != null) previous.run();
    }

    private static final class LuckPermsHook {
        static Runnable subscribe() {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            var subscription = api.getEventBus().subscribe(
                    net.luckperms.api.event.user.UserDataRecalculateEvent.class,
                    event -> SharedPermissionChanges.changed(event.getUser().getUniqueId()));
            return subscription::close;
        }
    }
}
