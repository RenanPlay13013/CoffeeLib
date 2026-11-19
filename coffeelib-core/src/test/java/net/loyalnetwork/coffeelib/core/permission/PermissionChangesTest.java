package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class PermissionChangesTest {
    @AfterEach void clean() {
        SharedPermissionChanges.clearAll();
        SharedPermissionChanges.resumeServer();
    }

    private static DefaultPermissionManager manager(String owner) {
        return new DefaultPermissionManager(owner,
                new PermissionResolver(new PermissionNodes(), new PermissionPlatform() {
                    @Override public UUID playerId(Object subject) { return null; }
                    @Override public boolean isOperator(Object subject, int level) { return false; }
                }));
    }

    @Test void coalescesAndNotifiesOnFlushThread() throws Exception {
        var received = new ArrayList<UUID>();
        var threads = new ArrayList<Thread>();
        var change = manager("flight");
        change.onChange(id -> { received.add(id); threads.add(Thread.currentThread()); });
        UUID id = UUID.randomUUID();
        Thread worker = new Thread(() -> { SharedPermissionChanges.changed(id); SharedPermissionChanges.changed(id); });
        worker.start(); worker.join();
        assertTrue(received.isEmpty());
        SharedPermissionChanges.flushTick();
        assertEquals(List.of(id), received);
        assertEquals(Thread.currentThread(), threads.get(0));
    }

    @Test void sameTickMultiplePlayersAndReentrancy() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<UUID> received = new ArrayList<>();
        manager("a").onChange(id -> {
            received.add(id);
            if (id.equals(a)) SharedPermissionChanges.changed(b);
        });
        SharedPermissionChanges.changed(a);
        SharedPermissionChanges.flushTick();
        assertEquals(List.of(a), received);
        SharedPermissionChanges.flushTick();
        assertEquals(List.of(a, b), received);
    }

    @Test void ownerReleaseAndUnsubscribeAreSafe() {
        List<UUID> received = new ArrayList<>();
        EventSubscription a = manager("a").onChange(received::add);
        EventSubscription b = manager("b").onChange(received::add);
        assertEquals(UUID.class, a.eventType());
        a.unsubscribe(); a.unsubscribe();
        assertFalse(a.isActive());
        SharedPermissionChanges.changed(UUID.randomUUID());
        SharedPermissionChanges.flushTick();
        assertEquals(1, received.size());
        SharedPermissionChanges.releaseOwner("b");
        assertFalse(b.isActive());
        SharedPermissionChanges.changed(UUID.randomUUID());
        SharedPermissionChanges.flushTick();
        assertEquals(1, received.size());
    }

    @Test void badSubscriberDoesNotBlockOthers() {
        var received = new ArrayList<UUID>();
        manager("bad").onChange(id -> { throw new AssertionError("expected assertion"); });
        manager("good").onChange(received::add);
        UUID id = UUID.randomUUID();
        SharedPermissionChanges.changed(id);
        SharedPermissionChanges.flushTick();
        assertEquals(List.of(id), received);
    }

    @Test void lateAsyncChangesAfterStopCannotEscapeIntoNextIntegratedWorld() {
        List<UUID> received = new ArrayList<>();
        manager("mod").onChange(received::add);
        SharedPermissionChanges.changed(UUID.randomUUID());
        SharedPermissionChanges.stopServer();
        SharedPermissionChanges.changed(UUID.randomUUID());
        SharedPermissionChanges.resumeServer();
        SharedPermissionChanges.flushTick();
        assertTrue(received.isEmpty());
    }

    @Test void stoppingServerDropsPendingAndNotListeners() {
        List<UUID> received = new ArrayList<>();
        EventSubscription live = manager("persistent-mod").onChange(received::add);
        SharedPermissionChanges.changed(UUID.randomUUID());
        SharedPermissionChanges.clearPending();
        SharedPermissionChanges.flushTick();
        assertTrue(received.isEmpty());
        SharedPermissionChanges.changed(UUID.randomUUID());
        SharedPermissionChanges.flushTick();
        assertEquals(1, received.size());
        assertTrue(live.isActive());
    }
}
