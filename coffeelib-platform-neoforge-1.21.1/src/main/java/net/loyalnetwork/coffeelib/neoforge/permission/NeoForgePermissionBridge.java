package net.loyalnetwork.coffeelib.neoforge.permission;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.core.permission.PermissionNodeCache;
import net.loyalnetwork.coffeelib.core.permission.PermissionNodes;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.handler.DefaultPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirrors CoffeeLib's permission nodes into NeoForge's PermissionAPI, so any
 * permission mod that is the server's permission handler sees and decides
 * them.
 * <p>
 * The PermissionAPI only takes nodes while the server starts
 * ({@link PermissionGatherEvent.Nodes}), and querying one it was not given
 * throws. So:
 * <ul>
 *   <li>at that event, every node declared so far is registered, plus every
 *       node in the {@link PermissionNodeCache} - those declared too late
 *       last time;</li>
 *   <li>a node declared after it cannot join until the next start. It is
 *       written to the cache and, until then, answered by CoffeeLib's own
 *       chain (LuckPerms directly, then its default) - {@link #check} returns
 *       {@code null} for it.</li>
 * </ul>
 * The PermissionAPI is consulted only when the active handler is not NeoForge's
 * own default one. That handler knows nothing of LuckPerms and would answer
 * with the node default alone, so on a server where LuckPerms is installed but
 * is not the configured handler, asking it would silently skip LuckPerms.
 */
public final class NeoForgePermissionBridge {

    private static final Logger LOGGER = Logger.getLogger(NeoForgePermissionBridge.class.getName());

    private static final Map<String, PermissionNode<Boolean>> REGISTERED = new ConcurrentHashMap<>();
    private static volatile boolean gathered;
    private static volatile PermissionNodeCache cache;
    private static volatile boolean installed;

    private NeoForgePermissionBridge() {
    }

    /** Hooks the gather event and late declarations. Called once, by the CoffeeLib mod. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, NeoForgePermissionBridge::onGather);
        SharedPermissions.nodes().addListener(NeoForgePermissionBridge::onDeclared);
    }

    /**
     * The PermissionAPI's answer for {@code subject} and {@code node}, or
     * {@code null} when it has none to give: not a player, node not registered
     * this run, or only NeoForge's default handler active.
     */
    public static Boolean check(Object subject, String node) {
        ServerPlayer player = player(subject);
        if (player == null) {
            return null;
        }
        PermissionNode<Boolean> registered = REGISTERED.get(node);
        if (registered == null) {
            return null;
        }
        try {
            if (DefaultPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
                return null;
            }
            return PermissionAPI.getPermission(player, registered);
        } catch (RuntimeException e) {
            // Not initialised yet, or a handler that throws: CoffeeLib's chain still answers.
            return null;
        }
    }

    /** Whether {@code node} was handed to the PermissionAPI this run. */
    public static boolean isRegistered(String node) {
        return REGISTERED.containsKey(node);
    }

    private static void onGather(PermissionGatherEvent.Nodes event) {
        // A new server (or, in single player, a new world): start from what is declared now.
        REGISTERED.clear();

        Map<String, PermissionNodes.NodeInfo> toRegister = new LinkedHashMap<>();
        for (PermissionNodes.NodeInfo cached : cache().prune(NeoForgePermissionBridge::ownerStillLoaded)) {
            toRegister.put(cached.node(), cached);
            // So its default is right even before its mod declares it again this run.
            SharedPermissions.nodes().declareIfAbsent(cached.ownerId(), cached.node(), cached.def(),
                    cached.operatorLevel(), cached.description());
        }
        for (PermissionNodes.NodeInfo declared : SharedPermissions.nodes().all()) {
            toRegister.put(declared.node(), declared);
        }

        for (PermissionNodes.NodeInfo info : toRegister.values()) {
            PermissionNode<Boolean> node = toPermissionNode(info);
            if (node != null && !event.getNodes().contains(node)) {
                event.addNodes(node);
                REGISTERED.put(info.node(), node);
            }
        }
        gathered = true;
        LOGGER.info("[CoffeeLib] Registered " + REGISTERED.size() + " permission node(s) with the PermissionAPI");
    }

    private static void onDeclared(PermissionNodes.NodeInfo info) {
        if (gathered && !REGISTERED.containsKey(info.node()) && toPermissionNode(info) != null) {
            cache().append(info);
        }
    }

    /**
     * {@code crates.give} as a PermissionNode whose name is still
     * {@code crates.give}: NeoForge builds the name as {@code namespace.path},
     * so the first segment is the namespace and the rest the path. A node with
     * one segment, or a wildcard, has no such form and stays with CoffeeLib's
     * chain.
     */
    private static PermissionNode<Boolean> toPermissionNode(PermissionNodes.NodeInfo info) {
        String node = info.node();
        int dot = node.indexOf('.');
        if (dot <= 0 || dot == node.length() - 1 || node.contains("*")) {
            return null;
        }
        String namespace = node.substring(0, dot);
        String path = node.substring(dot + 1);
        if (ResourceLocation.tryBuild(namespace, path) == null) {
            return null;
        }
        try {
            PermissionNode<Boolean> permission = new PermissionNode<>(namespace, path, PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> defaultFor(node, player));
            permission.setInformation(Component.literal(node),
                    Component.literal(info.description().isEmpty() ? node : info.description()));
            return permission;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "[CoffeeLib] Could not build a PermissionAPI node for '" + node + "'", e);
            return null;
        }
    }

    /** What a handler falls back to: the node's current declared default and op level. */
    private static Boolean defaultFor(String node, ServerPlayer player) {
        return switch (SharedPermissions.nodes().defaultOf(node)) {
            case EVERYONE -> true;
            case OPERATORS -> player != null && player.hasPermissions(SharedPermissions.nodes().operatorLevelOf(node));
            case NOBODY -> false;
        };
    }

    private static boolean ownerStillLoaded(PermissionNodes.NodeInfo info) {
        return ModList.get() == null || ModList.get().isLoaded(info.ownerId());
    }

    private static ServerPlayer player(Object subject) {
        if (subject instanceof ServerPlayer player) {
            return player;
        }
        if (subject instanceof CommandSourceStack stack) {
            return stack.getPlayer();
        }
        if (subject instanceof CommandSource source && source.unwrap() != source) {
            return player(source.unwrap());
        }
        return null;
    }

    private static PermissionNodeCache cache() {
        PermissionNodeCache current = cache;
        if (current == null) {
            current = new PermissionNodeCache(FMLPaths.CONFIGDIR.get().resolve("coffeelib")
                    .resolve("permission-nodes.cache"));
            cache = current;
        }
        return current;
    }
}
