package net.loyalnetwork.coffeelib.core.scheduler;

import net.loyalnetwork.coffeelib.api.scheduler.Task;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tick-driven scheduler Forge and NeoForge use, with the main thread faked by a flag. */
class TickSchedulerTest {

    private final AtomicBoolean onMain = new AtomicBoolean(false);
    private final TickScheduler scheduler = new TickScheduler("testmod", onMain::get);
    private final List<String> ran = new ArrayList<>();

    /** A tick as the platform runs it: on the main thread. */
    private void tick() {
        onMain.set(true);
        try {
            scheduler.tick();
        } finally {
            onMain.set(false);
        }
    }

    @Test
    void onMainRunsAtOnceOnTheMainThreadAndWaitsForTheTickElsewhere() {
        scheduler.onMain(() -> ran.add("queued"));
        assertTrue(ran.isEmpty(), "off the main thread it waits");

        onMain.set(true);
        scheduler.onMain(() -> ran.add("inline"));
        assertEquals(List.of("inline"), ran, "on the main thread it runs now");
        onMain.set(false);

        tick();
        assertEquals(List.of("inline", "queued"), ran);
    }

    @Test
    void laterRunsAfterItsTicksAndZeroMeansTheNextTick() {
        scheduler.later(0, () -> ran.add("next"));
        scheduler.later(3, () -> ran.add("third"));

        tick();
        assertEquals(List.of("next"), ran);
        tick();
        assertEquals(List.of("next"), ran, "not yet: three ticks from when it was scheduled");
        tick();
        assertEquals(List.of("next", "third"), ran);
        tick();
        assertEquals(List.of("next", "third"), ran, "once only");
    }

    @Test
    void everyRepeatsUntilCancelled() {
        AtomicInteger runs = new AtomicInteger();
        Task task = scheduler.every(0, 2, runs::incrementAndGet);

        for (int i = 0; i < 6; i++) {
            tick();
        }
        assertEquals(3, runs.get(), "ticks 1, 3 and 5");

        task.cancel();
        tick();
        tick();
        assertEquals(3, runs.get());
        assertTrue(task.isCancelled());
    }

    @Test
    void aFailingTaskIsLoggedAndTheRestStillRun() {
        AtomicInteger runs = new AtomicInteger();
        scheduler.every(0, 1, () -> {
            runs.incrementAndGet();
            throw new IllegalStateException("bug");
        });
        scheduler.later(0, () -> ran.add("other"));

        tick();
        tick();

        assertEquals(List.of("other"), ran, "the other task ran despite the failure");
        assertEquals(2, runs.get(), "a repeating task keeps its schedule after failing");
    }

    @Test
    void stoppingCancelsEverythingButTheSchedulerServesTheNextServer() {
        Task repeating = scheduler.every(0, 1, () -> ran.add("old"));
        scheduler.onMain(() -> ran.add("queued"));

        scheduler.stop();
        tick();
        assertTrue(ran.isEmpty(), "nothing from before the stop runs");
        assertTrue(repeating.isCancelled());

        scheduler.later(0, () -> ran.add("new"));
        tick();
        assertEquals(List.of("new"), ran);
    }

    @Test
    void aFutureIsBroughtBackToTheMainThread() {
        CompletableFuture<String> fromDatabase = new CompletableFuture<>();
        List<Boolean> seenOnMain = new ArrayList<>();
        CompletableFuture<String> back = scheduler.onMain(fromDatabase);
        back.thenAccept(value -> seenOnMain.add(onMain.get()));

        fromDatabase.complete("row");
        assertFalse(back.isDone(), "it waits for the main thread");

        tick();
        assertEquals("row", back.join());
        assertEquals(List.of(true), seenOnMain, "what is chained on it runs on the main thread");
    }

    @Test
    void aFailedFutureFailsTheSameWayOnTheMainThread() {
        CompletableFuture<String> fromDatabase = new CompletableFuture<>();
        CompletableFuture<String> back = scheduler.onMain(fromDatabase);

        fromDatabase.completeExceptionally(new IllegalStateException("closed"));
        tick();

        assertTrue(back.isCompletedExceptionally());
        assertEquals("closed", assertThrows(Exception.class, back::join).getCause().getMessage());
    }

    @Test
    void badDelaysAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> scheduler.later(-1, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> scheduler.every(-1, 1, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> scheduler.every(0, 0, () -> { }));
    }

    @Test
    void theRegistryHandsOutOneSchedulerPerOwner() {
        assertTrue(TickSchedulers.forOwner("a") == TickSchedulers.forOwner("a"));
        assertFalse(TickSchedulers.forOwner("a") == TickSchedulers.forOwner("b"));
    }
}
