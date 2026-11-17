package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;

/**
 * The node registry every owner shares, so a node's declared default is the
 * one used wherever it is checked - by the owner, by another mod, or by
 * CoffeeLib's command system.
 */
public final class SharedPermissions {

    private static final PermissionNodes NODES = new PermissionNodes();

    private SharedPermissions() {
    }

    public static PermissionNodes nodes() {
        return NODES;
    }

    /**
     * The default a command's node gets when its owner did not declare it:
     * open to everyone when the command asks for op level 0, operators
     * otherwise - the same people the level alone would have let in.
     */
    public static PermissionDefault defaultForLevel(int level) {
        return level <= 0 ? PermissionDefault.EVERYONE : PermissionDefault.OPERATORS;
    }
}
