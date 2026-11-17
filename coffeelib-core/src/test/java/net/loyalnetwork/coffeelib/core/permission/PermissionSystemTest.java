package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import net.loyalnetwork.coffeelib.api.permission.annotation.Permission;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The resolution order, with a fake permission plugin standing in for LuckPerms. */
class PermissionSystemTest {

    /** A subject: a player (with an id) or not, and an operator level. */
    record Subject(UUID id, int opLevel) {
        static Subject player(int opLevel) {
            return new Subject(UUID.randomUUID(), opLevel);
        }

        static Subject console() {
            return new Subject(null, 4);
        }
    }

    static final PermissionPlatform PLATFORM = new PermissionPlatform() {
        @Override
        public UUID playerId(Object subject) {
            return ((Subject) subject).id();
        }

        @Override
        public boolean isOperator(Object subject, int level) {
            return ((Subject) subject).opLevel() >= level;
        }
    };

    /** What the fake permission plugin has been told: node grants per player. */
    final Map<UUID, Map<String, Boolean>> plugin = new HashMap<>();

    private PermissionResolver resolver(PermissionNodes nodes) {
        return new PermissionResolver(nodes, PLATFORM,
                (subject, player, node) -> plugin.getOrDefault(player, Map.of()).get(node));
    }

    @Test
    void withoutAPermissionPluginTheDeclaredDefaultDecides() {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("mod", "crates.open", PermissionDefault.EVERYONE, "");
        nodes.declare("mod", "crates.give", PermissionDefault.OPERATORS, "");
        nodes.declare("mod", "crates.secret", PermissionDefault.NOBODY, "");
        PermissionResolver resolver = resolver(nodes);

        Subject player = Subject.player(0);
        Subject op = Subject.player(2);

        assertTrue(resolver.has(player, "crates.open"));
        assertFalse(resolver.has(player, "crates.give"));
        assertTrue(resolver.has(op, "crates.give"));
        assertFalse(resolver.has(op, "crates.secret"), "not even an op, until a plugin grants it");
    }

    @Test
    void thePermissionPluginsAnswerWinsBothWays() {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("mod", "crates.give", PermissionDefault.OPERATORS, "");
        PermissionResolver resolver = resolver(nodes);

        Subject granted = Subject.player(0);
        Subject denied = Subject.player(4);
        plugin.put(granted.id(), Map.of("crates.give", true));
        plugin.put(denied.id(), Map.of("crates.give", false));

        assertTrue(resolver.has(granted, "crates.give"), "granted to a non-op");
        assertFalse(resolver.has(denied, "crates.give"), "denied to an op");
    }

    @Test
    void anUndecidedNodeFallsBackToTheDefault() {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("mod", "crates.open", PermissionDefault.EVERYONE, "");
        PermissionResolver resolver = resolver(nodes);

        Subject player = Subject.player(0);
        plugin.put(player.id(), Map.of("something.else", false));

        assertTrue(resolver.has(player, "crates.open"));
    }

    @Test
    void theConsoleHasEverythingItsLevelAllows() {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("mod", "crates.secret", PermissionDefault.NOBODY, "");
        PermissionResolver resolver = resolver(nodes);

        assertTrue(resolver.has(Subject.console(), "crates.secret"));
        assertTrue(resolver.has(Subject.console(), "never.declared"));
    }

    @Test
    void anUndeclaredNodeIsForOperatorsAndAnEmptyOneIsForEveryone() {
        PermissionResolver resolver = resolver(new PermissionNodes());

        assertFalse(resolver.has(Subject.player(0), "never.declared"));
        assertTrue(resolver.has(Subject.player(2), "never.declared"));
        assertTrue(resolver.has(Subject.player(0), ""));
    }

    @Test
    void theOperatorLevelFollowsTheCaller() {
        PermissionResolver resolver = resolver(new PermissionNodes());

        // A command declared at level 4 keeps needing 4 when no plugin decides.
        assertFalse(resolver.has(Subject.player(2), "server.reset", 4));
        assertTrue(resolver.has(Subject.player(4), "server.reset", 4));
    }

    @Test
    void anImpliedDeclarationDoesNotOverrideADeliberateOne() {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("mod", "crates.open", PermissionDefault.EVERYONE, "Open crates");
        nodes.declareIfAbsent("mod", "crates.open", PermissionDefault.OPERATORS, "from a command");

        assertEquals(PermissionDefault.EVERYONE, nodes.defaultOf("crates.open"));
        assertEquals("Open crates", nodes.descriptionOf("crates.open"));
    }

    public static final class CratePermissions {
        @Permission(def = PermissionDefault.EVERYONE, description = "Open crates")
        public static final String OPEN = "crates.open";

        @Permission(description = "Give keys")
        public static final String GIVE = "crates.give";

        public static final String NOT_DECLARED = "crates.ignored";
    }

    public static final class BadField {
        @Permission
        public final String node = "crates.x";
    }

    public static final class BadNode {
        @Permission
        public static final String NODE = "Crates Give";
    }

    @Test
    void annotatedConstantsAreDeclared() {
        PermissionNodes nodes = new PermissionNodes();
        DefaultPermissionManager manager = new DefaultPermissionManager("crates", resolver(nodes));

        manager.declare(CratePermissions.class);

        assertEquals(Set.of("crates.give", "crates.open"), manager.declared());
        assertEquals(PermissionDefault.EVERYONE, nodes.defaultOf(CratePermissions.OPEN));
        assertEquals(PermissionDefault.OPERATORS, nodes.defaultOf(CratePermissions.GIVE));
    }

    @Test
    void badDeclarationsAreRefused() {
        DefaultPermissionManager manager = new DefaultPermissionManager("crates", resolver(new PermissionNodes()));

        assertThrows(CoffeeLibException.class, () -> manager.declare(BadField.class));
        assertThrows(CoffeeLibException.class, () -> manager.declare(BadNode.class));
        assertThrows(CoffeeLibException.class, () -> manager.declare(Object.class));
        assertThrows(CoffeeLibException.class, () -> manager.declare("has space", PermissionDefault.EVERYONE, ""));
        manager.declare("crates.*", PermissionDefault.NOBODY, "wildcard segments are valid");
    }
}
