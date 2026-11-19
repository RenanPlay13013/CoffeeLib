package net.loyalnetwork.coffeelib.core.scheduler;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Every owner's {@link TickScheduler}, for the platforms without a scheduler
 * of their own: they install how to tell the main thread once, then tick and
 * stop all of them from their server events.
 */
public final class TickSchedulers {

    private static final Map<String, TickScheduler> SCHEDULERS = new ConcurrentHashMap<>();
    private static volatile BooleanSupplier onMainThread = () -> false;

    private TickSchedulers() {
    }

    /** How to tell whether the calling thread is the server's main thread. Set by the platform at startup. */
    public static void mainThread(BooleanSupplier check) {
        onMainThread = Objects.requireNonNull(check, "check");
    }

    /** {@code ownerId}'s scheduler, the same one every time. */
    public static TickScheduler forOwner(String ownerId) {
        return SCHEDULERS.computeIfAbsent(ownerId, id -> new TickScheduler(id, () -> onMainThread.getAsBoolean()));
    }

    /** One server tick for every owner. */
    public static void tickAll() {
        for (TickScheduler scheduler : SCHEDULERS.values()) {
            scheduler.tick();
        }
    }

    /** The server stopped: every owner's tasks are cancelled. */
    public static void stopAll() {
        for (TickScheduler scheduler : SCHEDULERS.values()) {
            scheduler.stop();
        }
    }
}
