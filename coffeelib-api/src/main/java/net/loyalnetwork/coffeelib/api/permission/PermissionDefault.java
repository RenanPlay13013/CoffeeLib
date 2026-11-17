package net.loyalnetwork.coffeelib.api.permission;

/**
 * Who has a permission when no permission plugin has said anything about it -
 * no LuckPerms installed, or LuckPerms installed with the node neither granted
 * nor denied.
 */
public enum PermissionDefault {

    /** Everyone, unless a permission plugin denies it. */
    EVERYONE,

    /**
     * Server operators (op level 2 on Forge/NeoForge, op on Paper) and the
     * console, unless a permission plugin says otherwise.
     */
    OPERATORS,

    /** No player until a permission plugin grants it. The console still has it. */
    NOBODY
}
