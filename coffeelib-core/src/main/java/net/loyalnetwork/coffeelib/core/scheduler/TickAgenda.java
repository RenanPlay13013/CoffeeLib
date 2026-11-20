package net.loyalnetwork.coffeelib.core.scheduler;

import net.loyalnetwork.coffeelib.api.scheduler.Task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Tick-based task aggregate. Its caller holds the scheduler monitor while
 * scheduling, selecting due tasks and stopping, keeping transitions atomic.
 */
final class TickAgenda {
    private final List<Timed> tasks = new ArrayList<>();
    private long clock;

    Task schedule(long delay, long period, Runnable task) {
        Timed timed = new Timed(Objects.requireNonNull(task, "task"), clock + delay, period);
        tasks.add(timed);
        return timed;
    }

    List<Timed> advance() {
        clock++;
        List<Timed> due = new ArrayList<>();
        Iterator<Timed> pending = tasks.iterator();
        while (pending.hasNext()) {
            Timed task = pending.next();
            if (task.cancelled) {
                pending.remove();
            } else if (task.nextRun <= clock) {
                due.add(task);
                if (task.period > 0) {
                    // A late execution resets the interval, matching Bukkit scheduling.
                    task.nextRun = clock + task.period;
                } else {
                    pending.remove();
                }
            }
        }
        return due;
    }

    void cancelAll() {
        tasks.forEach(Task::cancel);
        tasks.clear();
    }

    static final class Timed implements Task {
        private final Runnable action;
        private final long period;
        private long nextRun;
        private volatile boolean cancelled;

        Timed(Runnable action, long nextRun, long period) {
            this.action = action;
            this.nextRun = nextRun;
            this.period = period;
        }

        Runnable action() { return action; }

        @Override public void cancel() { cancelled = true; }
        @Override public boolean isCancelled() { return cancelled; }
    }
}
