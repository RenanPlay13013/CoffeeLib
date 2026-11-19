package net.loyalnetwork.coffeelib.core.scheduler;

import net.loyalnetwork.coffeelib.api.scheduler.Scheduler;
import net.loyalnetwork.coffeelib.api.scheduler.Task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link Scheduler} driven by the platform calling {@link #tick()} once per
 * server tick, on the main thread - what Forge and NeoForge need, as they have
 * no scheduler of their own. Paper delegates to Bukkit's instead.
 * <p>
 * Work handed over from other threads waits in a queue until the next tick;
 * from the main thread, {@link #onMain(Runnable)} runs at once.
 * {@link #stop()} cancels everything, and the scheduler stays usable for the
 * next server: what is scheduled while none runs waits for its first tick.
 */
public final class TickScheduler implements Scheduler {

    private static final Logger LOGGER = Logger.getLogger(TickScheduler.class.getName());

    private final String ownerId;
    private final BooleanSupplier onMainThread;
    private final Queue<Runnable> queued = new ConcurrentLinkedQueue<>();
    // Futures bridged to the server thread belong to the current server lifecycle.
    // Without cancellation, stop() can strand them forever or deliver old-world data
    // into the next integrated server when their database / I/O task finishes late.
    private final Set<CompletableFuture<?>> waitingOnMain = ConcurrentHashMap.newKeySet();
    private final List<Timed> timed = new ArrayList<>();
    private final Executor main = this::onMain;
    private long ticks;
    private volatile long lifecycleGeneration;

    /**
     * @param ownerId      the mod the tasks belong to, for log lines
     * @param onMainThread whether the calling thread is the server's main thread
     */
    public TickScheduler(String ownerId, BooleanSupplier onMainThread) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.onMainThread = Objects.requireNonNull(onMainThread, "onMainThread");
    }

    @Override
    public void onMain(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (onMainThread.getAsBoolean()) {
            run(task);
        } else {
            queued.add(task);
        }
    }

    @Override
    public Executor main() {
        return main;
    }

    @Override
    public Task later(long ticks, Runnable task) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative: " + ticks);
        }
        return schedule(ticks, 0, task);
    }

    @Override
    public Task every(long delay, long period, Runnable task) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay must not be negative: " + delay);
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive: " + period);
        }
        return schedule(delay, period, task);
    }

    @Override
    public <T> CompletableFuture<T> onMain(CompletableFuture<T> future) {
        Objects.requireNonNull(future, "future");
        CompletableFuture<T> result = new CompletableFuture<>();
        final long createdInGeneration;
        synchronized (this) {
            createdInGeneration = lifecycleGeneration;
            waitingOnMain.add(result);
        }
        result.whenComplete((value, error) -> waitingOnMain.remove(result));
        future.whenComplete((value, error) -> {
            if (result.isDone()) {
                return; // The server stopped while the original future was pending.
            }
            onMain(() -> {
                if (result.isDone()) {
                    return; // The server stopped after the completion was queued.
                }
                if (lifecycleGeneration != createdInGeneration) {
                    result.completeExceptionally(new CancellationException("Server lifecycle changed"));
                } else if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(value);
                }
            });
        });
        return result;
    }

    /**
     * One server tick: what other threads queued, then the timed tasks that
     * are due. Called by the platform on the main thread.
     */
    public void tick() {
        List<Timed> due = new ArrayList<>();
        synchronized (this) {
            ticks++;
            Iterator<Timed> pending = timed.iterator();
            while (pending.hasNext()) {
                Timed task = pending.next();
                if (task.cancelled) {
                    pending.remove();
                } else if (task.nextRun <= ticks) {
                    due.add(task);
                    if (task.period > 0) {
                        // From this run, not from when it was due: a late first run shifts the rest.
                        task.nextRun = ticks + task.period;
                    } else {
                        pending.remove();
                    }
                }
            }
        }

        // Only what was queued before this tick: a thread queueing without pause cannot hold the tick here.
        for (int left = queued.size(); left > 0; left--) {
            Runnable task = queued.poll();
            if (task == null) {
                break;
            }
            run(task);
        }
        for (Timed task : due) {
            if (!task.cancelled) {
                run(task.task);
            }
        }
    }

    /** Cancels scheduled tasks and pending server-thread futures: the server stopped. */
    public void stop() {
        final List<CompletableFuture<?>> abandoned;
        synchronized (this) {
            lifecycleGeneration++;
            for (Timed task : timed) {
                task.cancelled = true;
            }
            timed.clear();
            abandoned = List.copyOf(waitingOnMain);
        }
        // Complete outside the lock: client continuations may execute inline.
        // This must happen even if the original database / I/O future never completes.
        for (CompletableFuture<?> future : abandoned) {
            future.completeExceptionally(new CancellationException(
                    "Server stopped before the future could complete on its main thread"));
        }
        queued.clear();
    }

    private synchronized Task schedule(long delay, long period, Runnable task) {
        Objects.requireNonNull(task, "task");
        Timed scheduled = new Timed(task, ticks + delay, period);
        timed.add(scheduled);
        return scheduled;
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError e) {
            LOGGER.log(Level.SEVERE, "[CoffeeLib/" + ownerId + "] A scheduled task failed", e);
        }
    }

    private static final class Timed implements Task {
        final Runnable task;
        final long period;
        long nextRun;
        volatile boolean cancelled;

        Timed(Runnable task, long nextRun, long period) {
            this.task = task;
            this.nextRun = nextRun;
            this.period = period;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
