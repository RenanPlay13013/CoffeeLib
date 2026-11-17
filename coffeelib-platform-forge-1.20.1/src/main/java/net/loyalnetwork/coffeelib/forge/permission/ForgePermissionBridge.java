package net.loyalnetwork.coffeelib.forge.permission;

import net.loyalnetwork.coffeelib.core.permission.PermissionApiMirror;
import net.loyalnetwork.coffeelib.core.permission.PermissionNodes;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.handler.DefaultPermissionHandler;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirrors CoffeeLib's permission nodes into Forge's PermissionAPI, so any
 * permission mod that is the server's permission handler sees and decides
 * them. The bookkeeping - the gather, late nodes and their cache - is
 * {@link PermissionApiMirror}'s; this class is the Forge side of it.
 * <p>
 * The PermissionAPI is consulted only when the active handler is not Forge's
 * own default one. That handler knows nothing of LuckPerms and would answer
 * with the node default alone, so on a server where LuckPerms is installed but
 * is not the configured handler, asking it would silently skip LuckPerms.
 */
public final class ForgePermissionBridge {

    private static final Logger LOGGER = Logger.getLogger(ForgePermissionBridge.class.getName());

    private static final PermissionApiMirror<PermissionNode<Boolean>> MIRROR = new PermissionApiMirror<>(
            SharedPermissions.nodes(), ForgePermissionBridge::toPermissionNode,
            info -> ModList.get() == null || ModList.get().isLoaded(info.ownerId()),
            () -> FMLPaths.CONFIGDIR.get().resolve("coffeelib").resolve("permission-nodes.cache"));

    private static volatile boolean installed;

    private ForgePermissionBridge() {
    }

    /** Hooks the gather event. Called once, by the CoffeeLib mod. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        // Lowest priority: other mods' own nodes are in the event by then, so a node they
        // registered themselves is recognised instead of being added a second time.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PermissionGatherEvent.Nodes.class,
                ForgePermissionBridge::onGather);
    }

    /**
     * The PermissionAPI's answer for {@code subject} and {@code node}, or
     * {@code null} when it has none to give: not a player, node not registered
     * this run, or only Forge's default handler active.
     */
    static Boolean check(Object subject, UUID playerId, String node) {
        ServerPlayer player = ForgePermissions.player(subject);
        if (player == null) {
            return null;
        }
        PermissionNode<Boolean> registered = MIRROR.registered(node);
        if (registered == null) {
            return null;
        }
        try {
            if (DefaultPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
                return null;
            }
            return PermissionAPI.getPermission(player, registered);
        } catch (RuntimeException e) {
            // Not initialised yet, or a handler that refuses this player (LuckPerms and a
            // fake player): CoffeeLib's chain still answers.
            return null;
        }
    }

    /** Whether {@code node} can be queried through the PermissionAPI this run. */
    public static boolean isRegistered(String node) {
        return MIRROR.registered(node) != null;
    }

    private static void onGather(PermissionGatherEvent.Nodes event) {
        Map<String, PermissionNode<?>> present = new HashMap<>();
        for (PermissionNode<?> node : event.getNodes()) {
            present.put(node.getNodeName(), node);
        }
        int count = MIRROR.gather(new PermissionApiMirror.Gather<>() {
            @Override
            public boolean contains(String name) {
                return present.containsKey(name);
            }

            @Override
            @SuppressWarnings("unchecked")
            public PermissionNode<Boolean> existingBoolean(String name) {
                PermissionNode<?> node = present.get(name);
                return node != null && node.getType() == PermissionTypes.BOOLEAN ? (PermissionNode<Boolean>) node : null;
            }

            @Override
            public void add(PermissionNode<Boolean> node) {
                event.addNodes(node);
                present.put(node.getNodeName(), node);
            }
        });
        LOGGER.info("[CoffeeLib] Registered " + count + " permission node(s) with the PermissionAPI");
    }

    /** {@code crates.give} as a PermissionNode whose name is still {@code crates.give}. */
    private static PermissionNode<Boolean> toPermissionNode(PermissionNodes.NodeInfo info, String namespace,
                                                            String path) {
        String node = info.node();
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
}
