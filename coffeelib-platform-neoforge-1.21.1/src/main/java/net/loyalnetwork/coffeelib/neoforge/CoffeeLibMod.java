package net.loyalnetwork.coffeelib.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Makes NeoForge's loader accept this jar as a mod, and registers the one
 * piece of behavior CoffeeLib runs itself: {@code /coffeelib config}.
 * <p>
 * That command is the operator side of the config formats CoffeeLib manages
 * itself (anything but
 * {@link net.loyalnetwork.coffeelib.api.config.ConfigFormat#NATIVE}) — manual
 * reload and start/stop of hot reload. It has to live here because NeoForge
 * registers commands through a game-bus event, and because the mods whose
 * configs it acts on hand CoffeeLib nothing; they are found through
 * {@link net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry}.
 * Everything else is still library code other mods call into via
 * {@link CoffeeLib#forMod}.
 */
@Mod("coffeelib")
public final class CoffeeLibMod {

    public CoffeeLibMod() {
        net.loyalnetwork.coffeelib.neoforge.permission.NeoForgePermissionBridge.install();
        new net.loyalnetwork.coffeelib.core.permission.DefaultPermissionManager("coffeelib",
                net.loyalnetwork.coffeelib.neoforge.permission.NeoForgePermissions.resolver()).declare(net.loyalnetwork.coffeelib.core.permission.CoffeeLibNodes.class);
        net.loyalnetwork.coffeelib.core.player.SharedPlayers.configureOnlinePlayers(() -> {
            var current = ServerLifecycleHooks.getCurrentServer();
            return current == null ? java.util.List.of() : current.getPlayerList().getPlayers();
        }, (viewer, target) -> true);
        // The game bus, not the mod bus: RegisterCommandsEvent fires per server start.
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::onRegisterCommands);
        // Every mod's databases, when the game exits: the pools' connections are released
        // on the database server rather than left for it to time out.
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class,
                event -> net.loyalnetwork.coffeelib.database.OpenDatabases.closeAll());
        // Permission changes are only invalidation signals: consumers re-read their nodes.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent.class, event -> {
            if (!event.isCanceled()) {
                net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges.changed(
                        event.getEntity().getUUID());
            }
        });
        // Every mod's scheduler: driven by the server's ticks, emptied when it stops.
        net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.mainThread(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            return server != null && server.isSameThread();
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> {
                    net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges.resumeServer();
                    net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.tickAll();
                    net.loyalnetwork.coffeelib.neoforge.permission.NeoForgePermissionEvents.onTick();
                    net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges.flushTick();
                });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class,
                event -> {
                    net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.stopAll();
                    net.loyalnetwork.coffeelib.neoforge.permission.NeoForgePermissionEvents.onStop();
                    net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges.stopServer();
                });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CoffeeLibCommands.register(event.getDispatcher());
    }
}
