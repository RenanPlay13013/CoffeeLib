package net.loyalnetwork.coffeelib.api.scheduler;

/** A task given to a {@link Scheduler}, to cancel it before it runs (again). */
public interface Task {

    /** Stops the task: it does not run again. Cancelling twice, or a task that already ran, does nothing. */
    void cancel();

    boolean isCancelled();
}
