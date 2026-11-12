package net.loyalnetwork.coffeelib.core.event;

import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.event.annotation.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared registry is the whole point of the bus: an event one owner posts
 * has to reach another owner's handlers, while cleanup stays per owner. Since
 * the registry is process-wide, every test here clears both owners afterwards
 * rather than leaking handlers into the next one.
 */
class SharedEventBusTest {

    record OrderPlaced(String item) {
    }

    private static final String MOD = "examplemod";
    private static final String PLUGIN = "ExamplePlugin";

    @AfterEach
    void clearOwners() {
        SharedEventBus.unregisterAll(MOD);
        SharedEventBus.unregisterAll(PLUGIN);
    }

    @Test
    void oneOwnerPostsAndAnotherOwnerReceives() {
        EventBus mod = SharedEventBus.forOwner(MOD);
        EventBus plugin = SharedEventBus.forOwner(PLUGIN);
        List<String> received = new ArrayList<>();

        plugin.subscribe(OrderPlaced.class, event -> received.add(event.item()));
        mod.post(new OrderPlaced("coffee"));

        assertEquals(List.of("coffee"), received);
    }

    @Test
    void twoHandlesForTheSameOwnerAreInterchangeable() {
        List<String> received = new ArrayList<>();
        SharedEventBus.forOwner(MOD).subscribe(OrderPlaced.class, event -> received.add(event.item()));

        // A handle carries nothing but the owner id, so asking for another one is free
        // and a caller never has to keep the first around.
        SharedEventBus.forOwner(MOD).post(new OrderPlaced("tea"));

        assertEquals(List.of("tea"), received);
    }

    @Test
    void unregisterAllTouchesOnlyItsOwnOwner() {
        List<String> modSeen = new ArrayList<>();
        List<String> pluginSeen = new ArrayList<>();

        EventBus mod = SharedEventBus.forOwner(MOD);
        EventBus plugin = SharedEventBus.forOwner(PLUGIN);
        mod.subscribe(OrderPlaced.class, event -> modSeen.add(event.item()));
        plugin.subscribe(OrderPlaced.class, event -> pluginSeen.add(event.item()));

        mod.unregisterAll();
        mod.post(new OrderPlaced("coffee"));

        assertTrue(modSeen.isEmpty(), "the owner that cleaned up should hear nothing");
        assertEquals(List.of("coffee"), pluginSeen, "the other owner keeps working");
        assertEquals(0, SharedEventBus.handlerCount(MOD));
        assertEquals(1, SharedEventBus.handlerCount(PLUGIN));
    }

    @Test
    void theStaticCleanupPathDropsAnOwnersHandlers() {
        List<String> seen = new ArrayList<>();

        class Listener {
            @Subscribe
            void onOrder(OrderPlaced event) {
                seen.add(event.item());
            }
        }

        SharedEventBus.forOwner(PLUGIN).register(new Listener());
        assertEquals(1, SharedEventBus.handlerCount(PLUGIN));

        // This is what the Paper plugin calls when a plugin is disabled: cleaning up an
        // owner it holds no handle to, so a reloaded plugin leaves nothing behind.
        SharedEventBus.unregisterAll(PLUGIN);

        SharedEventBus.forOwner(MOD).post(new OrderPlaced("coffee"));
        assertTrue(seen.isEmpty());
        assertEquals(0, SharedEventBus.handlerCount(PLUGIN));
    }

    @Test
    void aPrivateBusIsNotTheSharedOne() {
        List<String> shared = new ArrayList<>();
        List<String> privateSeen = new ArrayList<>();

        SharedEventBus.forOwner(MOD).subscribe(OrderPlaced.class, event -> shared.add(event.item()));
        EventBus own = new DefaultEventBus(MOD);
        own.subscribe(OrderPlaced.class, event -> privateSeen.add(event.item()));

        own.post(new OrderPlaced("private"));
        SharedEventBus.forOwner(PLUGIN).post(new OrderPlaced("shared"));

        // Which is exactly what a mod shading CoffeeLib ends up with, by accident.
        assertEquals(List.of("private"), privateSeen);
        assertEquals(List.of("shared"), shared);
    }
}
