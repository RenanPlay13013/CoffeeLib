package net.loyalnetwork.coffeelib.neoforge.permission;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.core.permission.PermissionPlatform;
import net.loyalnetwork.coffeelib.core.permission.PermissionResolver;
import net.loyalnetwork.coffeelib.core.permission.SharedPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Permission checks on NeoForge: the PermissionAPI when a permission mod
 * handles it, LuckPerms asked directly for what the PermissionAPI cannot
 * answer, the node's declared default otherwise.
 * <p>
 * Every place CoffeeLib checks a permission on this platform goes through
 * here - annotated commands, {@code /coffeelib}'s own subcommands, and
 * {@code PermissionManager#has} - so they all give the same answer.
 */
public final class NeoForgePermissions {

    private static final PermissionPlatform PLATFORM = new PermissionPlatform() {
        @Override
        public UUID playerId(Object subject) {
            ServerPlayer player = player(subject);
            return player == null ? null : player.getUUID();
        }

        @Override
        public boolean isOperator(Object subject, int level) {
            if (subject instanceof ServerPlayer player) {
                return player.hasPermissions(level);
            }
            if (subject instanceof CommandSourceStack stack) {
                return stack.hasPermission(level);
            }
            if (subject instanceof CommandSource source && source.unwrap() != source) {
                return isOperator(source.unwrap(), level);
            }
            return false;
        }
    };

    private static final PermissionResolver RESOLVER = new PermissionResolver(SharedPermissions.nodes(), PLATFORM,
            NeoForgePermissionBridge::check, NeoForgeLuckPerms::check);

    private NeoForgePermissions() {
    }

    public static PermissionResolver resolver() {
        return RESOLVER;
    }

    /**
     * {@code node} for {@code subject}, with {@code operatorLevel} standing in
     * for "operators". An empty node falls back to the level alone - a
     * command without a permission node is gated by its op level, as before.
     */
    public static boolean check(Object subject, String node, int operatorLevel) {
        if (node == null || node.isEmpty()) {
            return operatorLevel <= 0 || PLATFORM.isOperator(subject, operatorLevel);
        }
        return RESOLVER.has(subject, node, operatorLevel);
    }

    /** Numeric grants from LuckPerms, when present; no database loads. */
    public static Integer number(Object subject, String prefix) {
        ServerPlayer player = player(subject);
        return player == null ? null : NeoForgeLuckPerms.number(player, player.getUUID(), prefix);
    }

    /** The player behind {@code subject}: a player, a player's command source, or a CoffeeLib source wrapping one. */
    static ServerPlayer player(Object subject) {
        if (subject instanceof ServerPlayer player) {
            return player;
        }
        if (subject instanceof CommandSourceStack stack) {
            return stack.getPlayer();
        }
        if (subject instanceof CommandSource source) {
            Object unwrapped = source.unwrap();
            return unwrapped == source ? null : player(unwrapped);
        }
        return null;
    }
}
