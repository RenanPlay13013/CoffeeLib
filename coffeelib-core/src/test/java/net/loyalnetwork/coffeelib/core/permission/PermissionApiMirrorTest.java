package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gather/late-node bookkeeping Forge and NeoForge share, against a fake PermissionAPI. */
class PermissionApiMirrorTest {

    /** A PermissionAPI's gather event: nodes by name, with a type, as strings "name:type". */
    static final class FakeGather implements PermissionApiMirror.Gather<String> {
        final Map<String, String> nodes = new HashMap<>();
        final List<String> added = new ArrayList<>();

        FakeGather with(String name, String type) {
            nodes.put(name, name + ":" + type);
            return this;
        }

        @Override
        public boolean contains(String name) {
            return nodes.containsKey(name);
        }

        @Override
        public String existingBoolean(String name) {
            String node = nodes.get(name);
            return node != null && node.endsWith(":boolean") ? node : null;
        }

        @Override
        public void add(String node) {
            if (nodes.putIfAbsent(node.substring(0, node.indexOf(':')), node) != null) {
                throw new IllegalArgumentException("duplicate " + node); // what the real API does
            }
            added.add(node);
        }
    }

    private static PermissionApiMirror<String> mirror(PermissionNodes nodes, Path cache) {
        return new PermissionApiMirror<>(nodes, (info, namespace, path) -> namespace + "." + path + ":boolean",
                info -> !info.ownerId().equals("removedmod"), () -> cache);
    }

    @Test
    void nodesDeclaredBeforeTheGatherAreRegisteredOnlyWhenTheyHaveANamespaceAndPath(@TempDir Path dir) {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("crates", "crates.give", PermissionDefault.OPERATORS, "");
        nodes.declare("crates", "crates", PermissionDefault.OPERATORS, "one segment");
        nodes.declare("crates", "crates.*", PermissionDefault.NOBODY, "wildcard");
        PermissionApiMirror<String> mirror = mirror(nodes, dir.resolve("cache"));

        FakeGather event = new FakeGather();
        assertEquals(1, mirror.gather(event));

        assertEquals(List.of("crates.give:boolean"), event.added);
        assertEquals("crates.give:boolean", mirror.registered("crates.give"));
        assertNull(mirror.registered("crates"));
    }

    @Test
    void aNodeAnotherModRegisteredIsNotAddedTwice(@TempDir Path dir) {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("crates", "crates.give", PermissionDefault.OPERATORS, "");
        nodes.declare("crates", "crates.limit", PermissionDefault.OPERATORS, "");
        PermissionApiMirror<String> mirror = mirror(nodes, dir.resolve("cache"));

        FakeGather event = new FakeGather().with("crates.give", "boolean").with("crates.limit", "integer");
        mirror.gather(event); // the fake throws on a duplicate, as the PermissionAPI does

        assertTrue(event.added.isEmpty());
        assertEquals("crates.give:boolean", mirror.registered("crates.give"), "their boolean node is queried");
        assertNull(mirror.registered("crates.limit"), "a non-boolean one is left to the rest of the chain");
    }

    @Test
    void aLateNodeIsCachedAndRegisteredOnTheNextStart(@TempDir Path dir) {
        Path cache = dir.resolve("permission-nodes.cache");
        PermissionNodes firstRun = new PermissionNodes();
        PermissionApiMirror<String> mirror = mirror(firstRun, cache);
        mirror.gather(new FakeGather());

        firstRun.declare("crates", "crates.reset", PermissionDefault.NOBODY, 4, "late");
        assertNull(mirror.registered("crates.reset"), "too late for this run");

        PermissionNodes secondRun = new PermissionNodes();
        PermissionApiMirror<String> restarted = mirror(secondRun, cache);
        FakeGather event = new FakeGather();
        restarted.gather(event);

        assertEquals(List.of("crates.reset:boolean"), event.added);
        assertEquals(PermissionDefault.NOBODY, secondRun.defaultOf("crates.reset"), "its default comes back with it");
        assertEquals(4, secondRun.operatorLevelOf("crates.reset"));
    }

    @Test
    void aLateNodeReDeclaredWithAnotherDefaultIsCachedWithTheNewOne(@TempDir Path dir) {
        Path cache = dir.resolve("permission-nodes.cache");
        PermissionNodes nodes = new PermissionNodes();
        mirror(nodes, cache).gather(new FakeGather());

        nodes.declare("crates", "crates.open", PermissionDefault.EVERYONE, "");
        nodes.declare("crates", "crates.open", PermissionDefault.NOBODY, "now locked");

        PermissionNodes.NodeInfo cached = new PermissionNodeCache(cache).read().get("crates.open");
        assertEquals(PermissionDefault.NOBODY, cached.def());
        assertEquals("now locked", cached.description());
    }

    @Test
    void cachedNodesOfModsThatAreGoneAreNotRegistered(@TempDir Path dir) {
        Path cache = dir.resolve("permission-nodes.cache");
        PermissionNodeCache file = new PermissionNodeCache(cache);
        file.record(new PermissionNodes.NodeInfo("old.thing", "removedmod", PermissionDefault.NOBODY, 2, ""));
        file.record(new PermissionNodes.NodeInfo("crates.reset", "crates", PermissionDefault.NOBODY, 2, ""));

        FakeGather event = new FakeGather();
        mirror(new PermissionNodes(), cache).gather(event);

        assertEquals(List.of("crates.reset:boolean"), event.added);
    }

    @Test
    void aNewServerStartsFromWhatIsDeclaredThen(@TempDir Path dir) {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("crates", "crates.give", PermissionDefault.OPERATORS, "");
        PermissionApiMirror<String> mirror = mirror(nodes, dir.resolve("cache"));
        mirror.gather(new FakeGather());

        FakeGather secondWorld = new FakeGather();
        mirror.gather(secondWorld);

        assertEquals(List.of("crates.give:boolean"), secondWorld.added, "registered again, not skipped as known");
    }
}
