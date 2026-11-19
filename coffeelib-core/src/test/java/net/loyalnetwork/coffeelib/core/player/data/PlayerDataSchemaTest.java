package net.loyalnetwork.coffeelib.core.player.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDataSchemaTest {
    @Test void keepsNamespacesIsolatedAndRejectsDuplicates() {
        var first = new PlayerDataSchema("raven");
        var other = new PlayerDataSchema("coffee");
        first.declare("god", PlayerDataSchema.Kind.FLAG, Boolean.class, false);
        other.declare("god", PlayerDataSchema.Kind.INTEGER, Integer.class, true);
        assertThrows(IllegalArgumentException.class, () -> first.declare("god", PlayerDataSchema.Kind.FLAG, Boolean.class, true));
    }

    @Test void rejectsInvalidKeysAndOwner() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerDataSchema("Invalid Owner"));
        var schema = new PlayerDataSchema("raven");
        assertThrows(IllegalArgumentException.class, () -> schema.declare("Uppercase", PlayerDataSchema.Kind.TEXT, String.class, false));
        assertThrows(IllegalArgumentException.class, () -> schema.declare("", PlayerDataSchema.Kind.TEXT, String.class, false));
    }

    @Test void deathPolicyIsPerKeyAndCannotRetroactivelyChange() {
        var schema = new PlayerDataSchema("raven");
        schema.declare("god", PlayerDataSchema.Kind.FLAG, Boolean.class, true);
        schema.declare("flight", PlayerDataSchema.Kind.FLAG, Boolean.class, false);
        var resets = schema.transientOnDeath();
        assertEquals(1, resets.size());
        assertEquals("raven:flight", resets.get(0).identifier());
    }

    @Test void conversionDefaultsAreStrict() {
        assertFalse(PlayerDataValues.decodeFlag(null));
        assertEquals(0, PlayerDataValues.decodeInteger(null));
        assertEquals("", PlayerDataValues.decodeText(null));
        assertTrue(PlayerDataValues.decodeFlag(PlayerDataValues.encodeFlag(true)));
        assertEquals(2147483647, PlayerDataValues.decodeInteger(PlayerDataValues.encodeInteger(Integer.MAX_VALUE)));
        assertThrows(IllegalStateException.class, () -> PlayerDataValues.decodeInteger("NaN"));
        assertThrows(IllegalStateException.class, () -> PlayerDataValues.decodeFlag("yes"));
        assertThrows(NullPointerException.class, () -> PlayerDataValues.encodeText(null));
    }
}
