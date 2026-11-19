package net.loyalnetwork.coffeelib.api.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Runs work on the server's main thread - now, later, or repeatedly - the
 * same way on every platform.
 * <pre>{@code
 * Scheduler scheduler = CoffeeLib.scheduler(container);   // Forge: scheduler(), Paper: scheduler(this)
 *
 * scheduler.later(20, () -> player.sendSystemMessage(...));          // in one second
 * Task check = scheduler.every(0, 20 * 60, this::saveAll);           // every minute
 *
 * repository.findById(id)
 *         .thenAcceptAsync(found -> teleport(player, found), scheduler.main());   // back on the server thread
 * }</pre>
 * Time is counted in server ticks, 20 a second when the server keeps up.
 * Each task runs on the main thread; an exception in one is logged under the
 * owner's id and does not stop the others, nor the next run of a repeating
 * one.
 * <p>
 * Every task of an owner is cancelled when the server stops (Forge, NeoForge)
 * or the plugin is disabled (Paper). Work scheduled while no server runs - in a
 * mod's constructor, or between two worlds of a client - waits for the first
 * tick of the next one.
 */
public interface Scheduler {

    /** Runs {@code task} on the main thread: right away if called from it, otherwise on the next tick. */
    void onMain(Runnable task);

    /**
     * The main thread as an {@link Executor}, for {@code thenAcceptAsync(...,
     * scheduler.main())} and anything else that takes one.
     */
    Executor main();

    /**
     * Runs {@code task} once, {@code ticks} ticks from now; {@code 0} and
     * {@code 1} both mean the next tick, as on Bukkit.
     *
     * @throws IllegalArgumentException when {@code ticks} is negative
     */
    Task later(long ticks, Runnable task);

    /**
     * Runs {@code task} every {@code period} ticks, the first time
     * {@code delay} ticks from now ({@code 0}: the next tick). The period
     * counts from each run, so a run delayed by a slow tick shifts the
     * following ones instead of bunching them up.
     *
     * @throws IllegalArgumentException when {@code delay} is negative or {@code period} is not positive
     */
    Task every(long delay, long period, Runnable task);

    /**
     * {@code future}, completing on the main thread: what is chained on the
     * returned future runs there, so it may touch the world. The result or
     * failure is the same as {@code future}'s.
     */
    <T> CompletableFuture<T> onMain(CompletableFuture<T> future);
}
