package net.loyalnetwork.coffeelib.database.location;

import jakarta.persistence.Embeddable;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CoffeeLocationTest {
    private static final Instant TIME = Instant.parse("2026-11-19T00:00:00Z");

    @Test void snapshotKeepsAllDataWithoutMinecraftTypes() {
        CoffeeLocation value = new CoffeeLocation("minecraft:the_nether", 20.5, 70, -5.5, 180, 15, TIME);
        assertEquals("minecraft:the_nether", value.world());
        assertEquals(20.5, value.x());
        assertEquals(70.0, value.y());
        assertEquals(-5.5, value.z());
        assertEquals(180.0f, value.yaw());
        assertEquals(15.0f, value.pitch());
        assertEquals(TIME, value.savedAt());
    }

    @Test void allNonFiniteCoordinatesAndAnglesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CoffeeLocation("world", Double.NaN, 1, 1, 0, 0, TIME));
        assertThrows(IllegalArgumentException.class, () -> new CoffeeLocation("world", 1, Double.POSITIVE_INFINITY, 1, 0, 0, TIME));
        assertThrows(IllegalArgumentException.class, () -> new CoffeeLocation("world", 1, 1, Double.NEGATIVE_INFINITY, 0, 0, TIME));
        assertThrows(IllegalArgumentException.class, () -> new CoffeeLocation("world", 1, 1, 1, Float.NaN, 0, TIME));
        assertThrows(IllegalArgumentException.class, () -> new CoffeeLocation("world", 1, 1, 1, 0, Float.POSITIVE_INFINITY, TIME));
    }

    @Test void requiredIdentityAndTimestampAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new CoffeeLocation(" ", 1, 1, 1, 0, 0, TIME));
        assertThrows(NullPointerException.class, () -> new CoffeeLocation("world", 1, 1, 1, 0, 0, null));
    }

    @Test void equalityWorksForEquivalentSnapshots() {
        CoffeeLocation one = new CoffeeLocation("world", 1, 2, 3, 4, 5, TIME);
        CoffeeLocation two = new CoffeeLocation("world", 1, 2, 3, 4, 5, TIME);
        assertTrue(one.equals(two));
        assertEquals(one.hashCode(), two.hashCode());
        assertFalse(one.equals(new CoffeeLocation("world2", 1, 2, 3, 4, 5, TIME)));
    }

    @Test void jpaCanCreateAnEmptyEmbeddableWithFieldAccess() throws Exception {
        assertNotNull(CoffeeLocation.class.getAnnotation(Embeddable.class));
        var constructor = CoffeeLocation.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
