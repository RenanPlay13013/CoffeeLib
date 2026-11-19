package net.loyalnetwork.coffeelib.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes Forge's loader accept this jar as a mod, and registers the one piece
 * of behavior CoffeeLib runs itself: {@code /coffeelib config}.
 * <p>
 * That command is the operator side of the config formats CoffeeLib manages
 * itself (anything but
 * {@link net.loyalnetwork.coffeelib.api.config.ConfigFormat#NATIVE}) — manual
 * reload and start/stop of hot reload. It has to live here because Forge
 * registers commands through a game-bus event, and because the mods whose
 * configs it acts on hand CoffeeLib nothing; they are found through
 * {@link net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry}.
 * Everything else is still library code other mods call into via
 * {@link CoffeeLib#forMod}.
 */
@Mod("coffeelib")
public final class CoffeeLibMod {

    public CoffeeLibMod() {
        net.loyalnetwork.coffeelib.forge.permission.ForgePermissionBridge.install();
        new net.loyalnetwork.coffeelib.core.permission.DefaultPermissionManager("coffeelib",
                net.loyalnetwork.coffeelib.forge.permission.ForgePermissions.resolver()).declare(net.loyalnetwork.coffeelib.core.permission.CoffeeLibNodes.class);
        // The game bus, not the mod bus: RegisterCommandsEvent fires per server start.
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        // Every mod's databases, when the game exits: the pools' connections are released
        // on the database server rather than left for it to time out.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, GameShuttingDownEvent.class,
                event -> net.loyalnetwork.coffeelib.database.OpenDatabases.closeAll());
        // Every mod's scheduler: driven by the server's ticks, emptied when it stops.
        net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.mainThread(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            return server != null && server.isSameThread();
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.tickAll();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class,
                event -> net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.stopAll());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CoffeeLibCommands.register(event.getDispatcher());
    }
}
