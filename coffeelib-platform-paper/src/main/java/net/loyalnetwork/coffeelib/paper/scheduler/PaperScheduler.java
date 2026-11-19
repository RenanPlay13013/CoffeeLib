package net.loyalnetwork.coffeelib.paper.scheduler;

import net.loyalnetwork.coffeelib.api.scheduler.Scheduler;
import net.loyalnetwork.coffeelib.api.scheduler.Task;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Paper's {@link Scheduler}: Bukkit's scheduler, bound to the owning plugin,
 * so Bukkit cancels the tasks when the plugin is disabled.
 * <p>
 * Only scheduler methods every Bukkit since 1.8 has are used. A failing task
 * is logged on the plugin's logger and, when repeating, keeps its schedule.
 * Work handed over after the plugin was disabled is dropped, since Bukkit no
 * longer runs anything for it.
 */
public final class PaperScheduler implements Scheduler {

    private final Plugin plugin;
    private final Executor main = this::onMain;

    public PaperScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void onMain(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (Bukkit.isPrimaryThread()) {
            guarded(task).run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, guarded(task));
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
        Objects.requireNonNull(task, "task");
        return new PaperTask(Bukkit.getScheduler().runTaskLater(plugin, guarded(task), ticks));
    }

    @Override
    public Task every(long delay, long period, Runnable task) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay must not be negative: " + delay);
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive: " + period);
        }
        Objects.requireNonNull(task, "task");
        return new PaperTask(Bukkit.getScheduler().runTaskTimer(plugin, guarded(task), delay, period));
    }

    @Override
    public <T> CompletableFuture<T> onMain(CompletableFuture<T> future) {
        CompletableFuture<T> result = new CompletableFuture<>();
        future.whenComplete((value, error) -> onMain(() -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(value);
            }
        }));
        return result;
    }

    /** The task, with a failure logged under the plugin instead of reaching Bukkit's scheduler. */
    private Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.SEVERE, "A scheduled task failed", e);
            }
        };
    }

    /** {@code BukkitTask#isCancelled()} is missing before 1.12, so cancelling is tracked here as well. */
    private static final class PaperTask implements Task {
        private final BukkitTask task;
        private volatile boolean cancelled;

        PaperTask(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            cancelled = true;
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
