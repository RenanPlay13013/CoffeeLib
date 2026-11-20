package net.loyalnetwork.coffeelib.neoforge.location;

import net.loyalnetwork.coffeelib.database.location.CoffeeLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Server-side adapter for persisted locations in Neoforge 1.21.1. */
public final class Locations {
    private Locations() { }

    public static CoffeeLocation of(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        requireServerThread(player.getServer());
        return new CoffeeLocation(player.serverLevel().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(),
                player.getXRot(), Instant.now());
    }

    /** Return empty for invalid dimension names or a dimension not loaded on this server. */
    public static Optional<ServerLevel> world(MinecraftServer server, CoffeeLocation location) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(location, "location");
        requireServerThread(server);
        ResourceLocation id = ResourceLocation.tryParse(location.world());
        return id == null ? Optional.empty() : Optional.ofNullable(server.getLevel(
                ResourceKey.create(Registries.DIMENSION, id)));
    }

    /** Only callable on the server thread. Returns false when the player/world is unavailable. */
    public static boolean teleport(ServerPlayer player, CoffeeLocation destination) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(destination, "destination");
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        requireServerThread(server);
        if (player.hasDisconnected()) return false;
        Optional<ServerLevel> target = world(server, destination);
        if (target.isEmpty()) return false;
        player.teleportTo(target.get(), destination.x(), destination.y(), destination.z(),
                destination.yaw(), destination.pitch());
        // Cross-dimensional teleports may replace the ServerPlayer instance; resolve by UUID afterward.
        return true;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Locations must be accessed on the Minecraft server thread");
        }
    }
}
