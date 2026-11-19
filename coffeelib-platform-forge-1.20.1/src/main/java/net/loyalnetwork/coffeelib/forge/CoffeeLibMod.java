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
        net.loyalnetwork.coffeelib.core.player.SharedPlayers.configureOnlinePlayers(() -> {
            var current = ServerLifecycleHooks.getCurrentServer();
            return current == null ? java.util.List.of() : current.getPlayerList().getPlayers();
        }, (viewer, target) -> true);
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.entity.Entity.class, this::attachPlayerData);
        MinecraftForge.EVENT_BUS.addListener(this::clonePlayerData);
        // The game bus, not the mod bus: RegisterCommandsEvent fires per server start.
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        // Every mod's databases, when the game exits: the pools' connections are released
        // on the database server rather than left for it to time out.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, GameShuttingDownEvent.class,
                event -> net.loyalnetwork.coffeelib.database.OpenDatabases.closeAll());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                net.minecraftforge.event.entity.player.PermissionsChangedEvent.class, event -> {
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
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges.resumeServer();
                    net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.tickAll();
                net.loyalnetwork.coffeelib.forge.permission.ForgePermissionEvents.onTick();
                net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges.flushTick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class,
                event -> {
                    net.loyalnetwork.coffeelib.core.scheduler.TickSchedulers.stopAll();
                    net.loyalnetwork.coffeelib.forge.permission.ForgePermissionEvents.onStop();
                    net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges.stopServer();
                });
    }

    private void attachPlayerData(net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof net.minecraft.world.entity.player.Player) {
            var provider = new net.loyalnetwork.coffeelib.forge.player.ForgePlayerDataProvider();
            event.addCapability(new net.minecraft.resources.ResourceLocation("coffeelib", "player_data"), provider);
            event.addListener(provider::invalidate);
        }
    }

    private void clonePlayerData(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer original
                && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer replacement) {
            net.loyalnetwork.coffeelib.forge.player.ForgePlayerDataFactory.copyOnDeath(original, replacement);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CoffeeLibCommands.register(event.getDispatcher());
    }
}
