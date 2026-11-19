package net.loyalnetwork.coffeelib.database.location;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

/**
 * Persistable snapshot of a player's location. Stores only value data: no
 * World, Level or Player instances are retained across sessions/dimensions.
 * World ids are platform-local, not portable between unrelated servers.
 * This @Embeddable can be nested in entities; use @AttributeOverrides if
 * multiple locations occur on the same entity.
 */
@Embeddable
@Access(AccessType.FIELD)
public class CoffeeLocation {
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private Instant savedAt;

    /** Required by JPA/Hibernate; never instantiate directly. */
    protected CoffeeLocation() { }

    public CoffeeLocation(String world, double x, double y, double z,
                          float yaw, float pitch, Instant savedAt) {
        if (world == null || world.isBlank()) throw new IllegalArgumentException("world must not be blank");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.savedAt = Objects.requireNonNull(savedAt, "savedAt");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    public String world() { return world; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public Instant savedAt() { return savedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CoffeeLocation that)) return false;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0
                && Double.compare(z, that.z) == 0 && Float.compare(yaw, that.yaw) == 0
                && Float.compare(pitch, that.pitch) == 0 && world.equals(that.world)
                && savedAt.equals(that.savedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z, yaw, pitch, savedAt);
    }
}
