package net.loyalnetwork.coffeelib.paper.location;

import net.loyalnetwork.coffeelib.database.location.CoffeeLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Platform adapter for persisted locations (Spigot 1.8.8 compatible). */
public final class Locations {
    private Locations() { }

    /** Snapshot on the server's primary thread; do not call from asynchronous tasks. */
    public static CoffeeLocation of(Player player) {
        requireMainThread();
        Location at = Objects.requireNonNull(player, "player").getLocation();
        return new CoffeeLocation(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                at.getYaw(), at.getPitch(), Instant.now());
    }

    /** Missing/unloaded worlds do not become invented or silently default to the overworld. */
    public static Optional<World> world(Server server, CoffeeLocation saved) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(saved, "saved");
        requireMainThread();
        return Optional.ofNullable(server.getWorld(saved.world()));
    }

    /** True if Bukkit accepted the teleport; false for missing world/offline player. */
    public static boolean teleport(Player player, CoffeeLocation saved) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(saved, "saved");
        if (!player.isOnline()) return false;
        Optional<World> target = world(player.getServer(), saved);
        if (target.isEmpty()) return false;
        return player.teleport(new Location(target.get(), saved.x(), saved.y(), saved.z(),
                saved.yaw(), saved.pitch()));
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Locations must be accessed on the Bukkit server thread");
        }
    }
}
