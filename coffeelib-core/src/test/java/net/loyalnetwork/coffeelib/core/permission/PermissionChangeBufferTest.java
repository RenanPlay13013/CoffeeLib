package net.loyalnetwork.coffeelib.core.permission;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PermissionChangeBufferTest {
    @Test void drainsOneTickAtATimeAndDeduplicates() {
        PermissionChangeBuffer buffer = new PermissionChangeBuffer();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        buffer.enqueue(first);
        buffer.enqueue(first);
        assertEquals(Set.of(first), buffer.drain());
        buffer.enqueue(second);
        assertEquals(Set.of(second), buffer.drain());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test void stopRejectsLateNotificationsUntilRestart() {
        PermissionChangeBuffer buffer = new PermissionChangeBuffer();
        UUID id = UUID.randomUUID();
        buffer.enqueue(id);
        buffer.suspend();
        buffer.enqueue(id);
        assertTrue(buffer.drain().isEmpty());
        buffer.resume();
        buffer.enqueue(id);
        assertEquals(Set.of(id), buffer.drain());
    }
}
