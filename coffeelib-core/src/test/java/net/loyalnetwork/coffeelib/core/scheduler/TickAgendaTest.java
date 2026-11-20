package net.loyalnetwork.coffeelib.core.scheduler;

import net.loyalnetwork.coffeelib.api.scheduler.Task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickAgendaTest {
    @Test void executesOnceAndContinuesPeriodicWorkFromActualTick() {
        TickAgenda agenda = new TickAgenda();
        Runnable noop = () -> {};
        TickAgenda.Timed once = (TickAgenda.Timed) agenda.schedule(0, 0, noop);
        TickAgenda.Timed repeating = (TickAgenda.Timed) agenda.schedule(2, 3, noop);
        assertEquals(1, agenda.advance().size());
        assertEquals(1, agenda.advance().size());
        assertTrue(agenda.advance().isEmpty());
        assertTrue(agenda.advance().isEmpty());
        assertEquals(1, agenda.advance().size());
        agenda.cancelAll();
        assertTrue(repeating.isCancelled());
        assertTrue(agenda.advance().isEmpty());
        assertFalse(once.isCancelled());
    }

    @Test void cancelledTaskNeverBecomesDue() {
        TickAgenda agenda = new TickAgenda();
        Task task = agenda.schedule(1, 0, () -> {});
        task.cancel();
        assertTrue(agenda.advance().isEmpty());
    }
}
