package net.loyalnetwork.coffeelib.forge.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.loyalnetwork.coffeelib.core.permission.PermissionNumbers;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

/**
 * LuckPerms, asked directly through its API, for what the PermissionAPI
 * cannot answer - a node declared after the server started - or for every
 * node when LuckPerms is installed but is not the PermissionAPI's handler.
 * <p>
 * LuckPerms is an optional dependency: its API is on the compile classpath
 * only, and its types are used only in {@link Api}, which is not loaded until
 * LuckPerms is known to be installed.
 */
final class ForgeLuckPerms {

    private static final String MOD_ID = "luckperms";

    private static volatile Boolean installed;

    private ForgeLuckPerms() {
    }

    /** LuckPerms' answer, or {@code null}: not installed, not started yet, the player not loaded, or the node unset. */
    static Boolean check(Object subject, UUID player, String node) {
        return installed() ? Api.check(ForgePermissions.player(subject), player, node) : null;
    }

    /** Loaded, contextual metadata is preferred; effective granted nodes are the fallback. */
    static Integer number(ServerPlayer online, UUID player, String prefix) {
        return installed() ? Api.number(online, player, prefix) : null;
    }

    private static boolean installed() {
        Boolean known = installed;
        if (known == null) {
            ModList mods = ModList.get();
            if (mods == null) {
                return false; // too early to tell: ask again next time
            }
            known = mods.isLoaded(MOD_ID);
            installed = known;
        }
        return known;
    }

    private static final class Api {

        static Boolean check(ServerPlayer online, UUID player, String node) {
            LuckPerms luckPerms;
            try {
                luckPerms = LuckPermsProvider.get();
            } catch (IllegalStateException notLoaded) {
                return null;
            }
            CachedPermissionData data = permissionData(luckPerms, online, player);
            Tristate result = data == null ? Tristate.UNDEFINED : data.checkPermission(node);
            return result == Tristate.UNDEFINED ? null : result.asBoolean();
        }

        static Integer number(ServerPlayer online, UUID player, String prefix) {
            LuckPerms luckPerms;
            try { luckPerms = LuckPermsProvider.get(); }
            catch (IllegalStateException notLoaded) { return null; }
            User user = luckPerms.getUserManager().getUser(player);
            CachedPermissionData permissions = permissionData(luckPerms, online, player);
            if (permissions == null) return null;
            CachedMetaData meta = null;
            if (online != null) {
                try { meta = luckPerms.getPlayerAdapter(ServerPlayer.class).getMetaData(online); }
                catch (IllegalStateException | IllegalArgumentException notTracked) { /* user fallback */ }
            }
            if (meta == null && user != null) meta = user.getCachedData().getMetaData();
            Integer configured = meta == null ? null : PermissionNumbers.parse(meta.getMetaValue(prefix));
            return configured != null ? configured : PermissionNumbers.highestGranted(prefix, permissions.getPermissionMap());
        }

        /**
         * An online player's data through the player adapter, which applies
         * their current contexts (world, gamemode...). Otherwise - an offline
         * player, or a player LuckPerms does not track, such as a fake player
         * - the user if it happens to be loaded; never loaded here, since that
         * is I/O and this runs on the server thread.
         */
        private static CachedPermissionData permissionData(LuckPerms luckPerms, ServerPlayer online, UUID player) {
            if (online != null) {
                try {
                    return luckPerms.getPlayerAdapter(ServerPlayer.class).getPermissionData(online);
                } catch (IllegalStateException | IllegalArgumentException notTracked) {
                    // fall back to the user, if loaded
                }
            }
            User user = luckPerms.getUserManager().getUser(player);
            return user == null ? null : user.getCachedData().getPermissionData();
        }
    }
}
