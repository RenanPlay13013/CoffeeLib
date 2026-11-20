package net.loyalnetwork.coffeelib.paper.permission;

import net.loyalnetwork.coffeelib.core.permission.SharedPermissionChanges;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** A single poller for ALL CoffeeLib owners, rather than polling inside every consuming plugin. */
public final class PaperPermissionEvents implements Listener {
    private final JavaPlugin owner;
    private final Map<UUID, PermissionState> previous = new HashMap<>();
    private BukkitTask task;
    private Runnable luckPermsClose;
    private int ticks;
    private boolean luckPermsIncompatibleLogged;

    public PaperPermissionEvents(JavaPlugin owner) { this.owner = owner; }

    public void start() {
        SharedPermissionChanges.resumeServer();
        owner.getServer().getPluginManager().registerEvents(this, owner);
        // 1 tick means callbacks are always on the Bukkit main thread.
        task = Bukkit.getScheduler().runTaskTimer(owner, () -> {
            if (++ticks % 20 == 0) {
                ensureLuckPerms();
                pollPlayers();
            }
            SharedPermissionChanges.flushTick();
        }, 1L, 1L);
        ensureLuckPerms();
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        disconnectLuckPerms();
        previous.clear();
        SharedPermissionChanges.stopServer();
        SharedPermissionChanges.clearAll();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        previous.remove(event.getPlayer().getUniqueId());
        SharedPermissionChanges.changed(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        previous.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDisabled(PluginDisableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("LuckPerms")) {
            disconnectLuckPerms();
        }
    }

    private boolean luckPermsAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        return plugin != null && plugin.isEnabled();
    }

    private void ensureLuckPerms() {
        if (!luckPermsAvailable()) { disconnectLuckPerms(); return; }
        if (luckPermsClose != null) return;
        try {
            // Nested class avoids loading optional LuckPerms classes until it is installed.
            luckPermsClose = LuckPermsHook.subscribe(owner);
        } catch (IllegalStateException notReady) {
            // The provider may not be registered yet. Retry next polling cycle.
        } catch (LinkageError incompatible) {
            if (!luckPermsIncompatibleLogged) {
                luckPermsIncompatibleLogged = true;
                owner.getLogger().log(Level.WARNING, "LuckPerms event API could not be loaded", incompatible);
            }
        }
    }

    private void disconnectLuckPerms() {
        Runnable close = luckPermsClose;
        luckPermsClose = null;
        if (close != null) close.run();
    }

    private void pollPlayers() {
        // With LuckPerms, changed users arrive through its event; poll op status anyway.
        // Without it, compare effective values of all CoffeeLib-declared permission nodes.
        boolean fullPoll = luckPermsClose == null;
        var declared = fullPoll ? SharedPermissions.nodes().all() : java.util.List
                .<net.loyalnetwork.coffeelib.core.permission.PermissionNodes.NodeInfo>of();
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            online.add(id);
            Map<String, Boolean> currentNodes = new HashMap<>();
            if (fullPoll) {
                for (var node : declared) {
                    currentNodes.put(node.node(), player.hasPermission(node.node()));
                }
            }
            PermissionState now = new PermissionState(player.isOp(), currentNodes);
            PermissionState before = previous.put(id, now);
            if (before != null && (before.op != now.op
                    || (fullPoll && !before.nodes.equals(now.nodes)))) {
                SharedPermissionChanges.changed(id);
            }
        }
        previous.keySet().retainAll(online);
    }

    private static final class PermissionState {
        final boolean op;
        final Map<String, Boolean> nodes;
        PermissionState(boolean op, Map<String, Boolean> nodes) { this.op = op; this.nodes = nodes; }
    }

    private static final class LuckPermsHook {
        static Runnable subscribe(JavaPlugin owner) {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            var subscription = api.getEventBus().subscribe(owner,
                    net.luckperms.api.event.user.UserDataRecalculateEvent.class,
                    event -> SharedPermissionChanges.changed(event.getUser().getUniqueId()));
            return subscription::close;
        }
    }
}
