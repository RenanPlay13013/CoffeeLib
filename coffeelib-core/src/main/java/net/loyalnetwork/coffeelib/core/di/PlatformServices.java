package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.database.DatabaseManager;
import net.loyalnetwork.coffeelib.api.di.annotation.Provide;
import net.loyalnetwork.coffeelib.api.permission.PermissionManager;
import net.loyalnetwork.coffeelib.api.player.PlayerDirectory;
import net.loyalnetwork.coffeelib.api.scheduler.Scheduler;

/** The platform-neutral provider host used by each platform entry point. */
public final class PlatformServices {
    private final PermissionManager permissions;
    private final Scheduler scheduler;
    private final DatabaseManager database;
    private final PlayerDirectory players;

    public PlatformServices(PermissionManager permissions, Scheduler scheduler,
                            DatabaseManager database, PlayerDirectory players) {
        this.permissions = java.util.Objects.requireNonNull(permissions, "permissions");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.database = java.util.Objects.requireNonNull(database, "database");
        this.players = java.util.Objects.requireNonNull(players, "players");
    }

    @Provide public PermissionManager permissions() { return permissions; }
    @Provide public Scheduler scheduler() { return scheduler; }
    @Provide public DatabaseManager database() { return database; }
    @Provide public PlayerDirectory players() { return players; }
}
