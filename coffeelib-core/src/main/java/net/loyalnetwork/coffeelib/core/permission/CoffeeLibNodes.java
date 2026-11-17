package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.permission.annotation.Permission;

/**
 * CoffeeLib's own permission nodes, for its {@code /coffeelib} subcommands.
 * Declared the same way a consumer would declare theirs, so LuckPerms and the
 * default rules treat them alike. All default to operators, matching the op
 * level 2 these subcommands required before they had nodes.
 */
public final class CoffeeLibNodes {

    @Permission(description = "List CoffeeLib-managed configs and their hot-reload state")
    public static final String CONFIG_LIST = "coffeelib.config.list";

    @Permission(description = "Reload CoffeeLib-managed configs")
    public static final String CONFIG_RELOAD = "coffeelib.config.reload";

    @Permission(description = "Start, stop and inspect config hot reload")
    public static final String CONFIG_HOTRELOAD = "coffeelib.config.hotreload";

    @Permission(description = "List and test placeholders")
    public static final String PLACEHOLDER = "coffeelib.placeholder";

    private CoffeeLibNodes() {
    }
}
