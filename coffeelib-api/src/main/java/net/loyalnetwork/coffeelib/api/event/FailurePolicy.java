package net.loyalnetwork.coffeelib.api.event;

/**
 * What {@link EventBus#dispatch(Object, FailurePolicy)} does when a handler
 * throws.
 * <p>
 * {@link EventBus#post(Object)} always behaves as {@link #CONTINUE}: events
 * are mostly notifications, handlers belong to different owners, and one
 * owner's bug must not stop another owner from hearing about something. The
 * other policy is for the publisher that is coordinating work and has to know.
 */
public enum FailurePolicy {

    /**
     * Log the failure, record it, and run the remaining handlers. Every
     * eligible handler gets its turn; the result says which ones failed.
     */
    CONTINUE,

    /**
     * Stop at the first failure. Handlers after it do not run, and the result
     * records the failure that stopped delivery.
     * <p>
     * For events where a later handler builds on an earlier one's work, so
     * running it after the earlier one failed would act on a half-done state.
     */
    STOP
}
