package net.loyalnetwork.coffeelib.paper.permission;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import net.loyalnetwork.coffeelib.core.permission.DefaultPermissionManager;
import net.loyalnetwork.coffeelib.core.permission.PermissionPlatform;
import net.loyalnetwork.coffeelib.core.permission.PermissionResolver;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissions;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Paper's permission manager: Bukkit's own permission system decides.
 * <p>
 * Paper already has what Forge-like platforms lack - permission plugins,
 * LuckPerms included, hook into {@code Permissible#hasPermission} - so going
 * through it is the integration, and it also covers any other permission
 * plugin. Declaring a node registers it with the server's
 * {@link PluginManager}, with its default mapped onto Bukkit's
 * ({@code EVERYONE} -> {@code TRUE}, {@code OPERATORS} -> {@code OP},
 * {@code NOBODY} -> {@code FALSE}), which is what lets permission plugins
 * list it and gives ops the right access with no plugin at all.
 */
public final class PaperPermissionManager extends DefaultPermissionManager {

    /** Nodes are kept in the shared registry; Bukkit answers the checks, so the resolver never runs. */
    private static final PermissionResolver NODES_ONLY = new PermissionResolver(SharedPermissions.nodes(),
            new PermissionPlatform() {
                @Override
                public UUID playerId(Object subject) {
                    return null;
                }

                @Override
                public boolean isOperator(Object subject, int level) {
                    return false;
                }
            });

    private final JavaPlugin plugin;

    public PaperPermissionManager(JavaPlugin plugin) {
        super(plugin.getName(), NODES_ONLY);
        this.plugin = plugin;
    }

    @Override
    public boolean has(Object subject, String node) {
        return check(subject, node);
    }

    @Override
    protected void onDeclared(String node, PermissionDefault def, String description) {
        register(plugin.getServer().getPluginManager(), node, def, description);
    }

    /** {@code node} for {@code subject}, through Bukkit; an empty node is always granted. */
    public static boolean check(Object subject, String node) {
        if (node == null || node.isEmpty()) {
            return true;
        }
        Object target = subject instanceof CommandSource source ? source.unwrap() : subject;
        return target instanceof Permissible permissible && permissible.hasPermission(node);
    }

    /**
     * Declares a command's node with the default its op level implies, unless
     * it was already declared - by its owner, deliberately, or by another
     * command sharing it.
     */
    public static void declareCommandNode(JavaPlugin owner, String node, int permissionLevel, String description) {
        PermissionDefault def = SharedPermissions.defaultForLevel(permissionLevel);
        SharedPermissions.nodes().declareIfAbsent(owner.getName(), node, def, description);
        PluginManager manager = owner.getServer().getPluginManager();
        if (manager.getPermission(node) == null) {
            register(manager, node, SharedPermissions.nodes().defaultOf(node), description);
        }
    }

    private static void register(PluginManager manager, String node, PermissionDefault def, String description) {
        org.bukkit.permissions.PermissionDefault bukkitDefault = switch (def) {
            case EVERYONE -> org.bukkit.permissions.PermissionDefault.TRUE;
            case OPERATORS -> org.bukkit.permissions.PermissionDefault.OP;
            case NOBODY -> org.bukkit.permissions.PermissionDefault.FALSE;
        };
        Permission existing = manager.getPermission(node);
        if (existing != null) {
            existing.setDefault(bukkitDefault);
            existing.setDescription(description);
        } else {
            manager.addPermission(new Permission(node, description, bukkitDefault));
        }
    }
}
