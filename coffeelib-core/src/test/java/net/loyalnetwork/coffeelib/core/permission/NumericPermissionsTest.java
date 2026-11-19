package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NumericPermissionsTest {
    record Subject(UUID id) { }
    private static final PermissionPlatform PLATFORM = new PermissionPlatform() {
        public UUID playerId(Object subject) { return ((Subject) subject).id(); }
        public boolean isOperator(Object subject, int level) { return false; }
    };
    private final Subject player = new Subject(UUID.randomUUID());
    private final Map<String, Boolean> grants = new HashMap<>();

    private DefaultPermissionManager manager() {
        PermissionResolver resolver = new PermissionResolver(new PermissionNodes(), PLATFORM,
                (subject, id, node) -> grants.get(node));
        return new DefaultPermissionManager("homes", resolver);
    }

    @Test void rangeHasNoImplicitGrantAndReturnsFallback() {
        DefaultPermissionManager manager = manager();
        manager.declareRange("homes.limit", 1, 5);
        assertEquals(1L, manager.declared().stream().filter(x -> x.equals("homes.limit.1")).count());
        assertEquals(7, manager.number(player, "homes.limit", 7));
        assertFalse(manager.has(player, "homes.limit.5"), "declared limits must not be granted by default");
    }

    @Test void highestGrantedWinsWithoutCountingDeniedNodes() {
        DefaultPermissionManager manager = manager();
        manager.declareRange("homes.limit", 1, 10);
        grants.put("homes.limit.2", true);
        grants.put("homes.limit.8", false);
        grants.put("homes.limit.4", true);
        assertEquals(4, manager.number(player, "homes.limit", 0));
    }

    @Test void validProviderMetaWinsOverNodeScanning() {
        DefaultPermissionManager manager = new DefaultPermissionManager("homes",
                new PermissionResolver(new PermissionNodes(), PLATFORM), (subject, prefix) -> 17);
        manager.declareRange("homes.limit", 1, 3);
        assertEquals(17, manager.number(player, "homes.limit", 0));
    }

    @Test void invalidRangeDoesNotPartiallyRegister() {
        DefaultPermissionManager manager = manager();
        assertThrows(CoffeeLibException.class, () -> manager.declareRange("homes.limit", 0, 10_001));
        assertThrows(CoffeeLibException.class, () -> manager.declareRange("homes.limit", -1, 10));
        assertThrows(CoffeeLibException.class, () -> manager.declareRange("homes.*", 1, 2));
        assertEquals(0, manager.declared().size());
    }

    @Test void numericValueParserRejectsOverflowAndBadInput() {
        assertEquals(0, PermissionNumbers.parse("0"));
        assertEquals(2147483647, PermissionNumbers.parse("2147483647"));
        assertNull(PermissionNumbers.parse("2147483648"));
        assertNull(PermissionNumbers.parse("-1"));
        assertNull(PermissionNumbers.parse("4.5"));
        assertNull(PermissionNumbers.parse("+4"));
    }

    @Test void contextualPermissionMapHonorsFalseAndNamespace() {
        assertEquals(8, PermissionNumbers.highestGranted("homes.limit", Map.of(
                "homes.limit.3", true, "homes.limit.8", true, "homes.limit.999", false,
                "homes.limits.200", true, "homes.limit.wildcard", true)));
        assertNull(PermissionNumbers.highestGranted("homes.limit", Map.of("homes.limit.9", false)));
    }

    @Test void fallbackIsNotTreatedAsGrantedNode() {
        assertEquals(12, PermissionNumbers.highestDeclared("homes.limit",
                java.util.List.of("homes.limit.1", "homes.limit.12"), node -> true, 100));
        assertEquals(100, PermissionNumbers.highestDeclared("homes.limit", java.util.List.of("homes.limit.1"),
                node -> false, 100));
    }
}
