package net.loyalnetwork.coffeelib.core.event;

import net.loyalnetwork.coffeelib.api.event.Cancellable;
import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.event.EventFailure;
import net.loyalnetwork.coffeelib.api.event.EventPriority;
import net.loyalnetwork.coffeelib.api.event.EventResult;
import net.loyalnetwork.coffeelib.api.event.FailurePolicy;
import net.loyalnetwork.coffeelib.api.event.annotation.Subscribe;
import net.loyalnetwork.coffeelib.api.event.exception.EventDispatchException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the bus does when a handler throws, from a report against 3.3 that found
 * two things: the publisher is never told, and the two ways of registering a
 * handler disagreed about an {@code AssertionError}.
 * <p>
 * The consistency tests run each case through both routes on purpose - the
 * bug was precisely that the answer depended on which one a handler used.
 */
class EventFailureHandlingTest {

    static final class Reward {
        final List<String> log = new ArrayList<>();
    }

    static final class CancellableReward implements Cancellable {
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    /** One thing a handler can throw, usable from either registration route. */
    @FunctionalInterface
    interface Thrower {
        void run() throws Throwable;
    }

    /** Registers {@code thrower} as a lambda. */
    private static void registerAsLambda(EventBus bus, Thrower thrower) {
        bus.subscribe(Reward.class, event -> sneakyThrow(thrower));
    }

    /** Registers {@code thrower} as an {@code @Subscribe} method. */
    private static void registerAsAnnotated(EventBus bus, Thrower thrower) {
        bus.register(new Object() {
            @Subscribe
            void onReward(Reward event) throws Throwable {
                thrower.run();
            }
        });
    }

    private static void sneakyThrow(Thrower thrower) {
        try {
            thrower.run();
        } catch (Throwable t) {
            // Throws t as-is from inside a Consumer, which a checked exception could not
            // otherwise do - the same shape a real lambda that throws a Throwable would have.
            EventFailureHandlingTest.<RuntimeException>rethrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void rethrow(Throwable t) throws E {
        throw (E) t;
    }

    // ---------------------------------------------------------------- consistency

    @Test
    void anAssertionErrorIsContainedWhicheverWayTheHandlerWasRegistered() {
        // The reported inconsistency: the lambda's escaped to the poster, the annotated
        // method's was wrapped into an EventException and swallowed.
        for (Consumer<EventBus> route : List.<Consumer<EventBus>>of(
                bus -> registerAsLambda(bus, () -> {
                    throw new AssertionError("boom");
                }),
                bus -> registerAsAnnotated(bus, () -> {
                    throw new AssertionError("boom");
                }))) {

            DefaultEventBus bus = new DefaultEventBus("test");
            route.accept(bus);
            List<String> after = new ArrayList<>();
            bus.subscribe(Reward.class, EventPriority.LATE, event -> after.add("ran"));

            EventResult<Reward> result = bus.dispatch(new Reward());

            assertEquals(List.of("ran"), after, "the next handler still runs");
            assertEquals(1, result.failures().size());
            assertTrue(result.failures().get(0).cause() instanceof AssertionError);
        }
    }

    @Test
    void aFailureReachesTheResultExactlyAsThrownOnBothRoutes() {
        IOException checked = new IOException("disk full");
        IllegalStateException unchecked = new IllegalStateException("bad state");
        LinkageError linkage = new NoClassDefFoundError("other/mod/Missing");

        for (Throwable thrown : List.of(checked, unchecked, linkage)) {
            for (boolean annotated : new boolean[]{false, true}) {
                DefaultEventBus bus = new DefaultEventBus("test");
                Thrower thrower = () -> {
                    throw thrown;
                };
                if (annotated) {
                    registerAsAnnotated(bus, thrower);
                } else {
                    registerAsLambda(bus, thrower);
                }

                EventFailure failure = bus.dispatch(new Reward()).failures().get(0);

                // Not wrapped in an EventException, not unwrapped from anything: the same
                // object the handler threw, so what a publisher inspects is the real cause.
                assertSame(thrown, failure.cause(),
                        thrown.getClass().getSimpleName() + " via " + (annotated ? "@Subscribe" : "lambda"));
            }
        }
    }

    @Test
    void aCheckedExceptionFromAnAnnotatedMethodIsContainedToo() {
        DefaultEventBus bus = new DefaultEventBus("test");
        registerAsAnnotated(bus, () -> {
            throw new IOException("declared with throws");
        });

        // A method may legitimately declare "throws Exception"; that must not be the one
        // shape that escapes, nor the one that gets wrapped.
        EventResult<Reward> result = bus.dispatch(new Reward());
        assertTrue(result.failures().get(0).cause() instanceof IOException);
    }

    @Test
    void theFatalErrorsOfTheJvmAreNotSwallowed() {
        for (boolean annotated : new boolean[]{false, true}) {
            DefaultEventBus bus = new DefaultEventBus("test");
            Thrower thrower = () -> {
                throw new OutOfMemoryError("simulated");
            };
            if (annotated) {
                registerAsAnnotated(bus, thrower);
            } else {
                registerAsLambda(bus, thrower);
            }
            List<String> after = new ArrayList<>();
            bus.subscribe(Reward.class, EventPriority.LATE, event -> after.add("ran"));

            // The VM saying it cannot continue is not a handler's bug to contain, and nothing
            // after it can be trusted to run.
            assertThrows(OutOfMemoryError.class, () -> bus.post(new Reward()));
            assertTrue(after.isEmpty());
        }
    }

    @Test
    void aRunawayRecursionIsAHandlerBugAndIsContained() {
        DefaultEventBus bus = new DefaultEventBus("test");
        bus.subscribe(Reward.class, new Consumer<>() {
            @Override
            public void accept(Reward event) {
                accept(event);
            }
        });

        // StackOverflowError is a VirtualMachineError by ancestry, but it is exactly a handler
        // misbehaving, and the stack has unwound by the time it is caught.
        EventResult<Reward> result = bus.dispatch(new Reward());
        assertTrue(result.failures().get(0).cause() instanceof StackOverflowError);
    }

    // ------------------------------------------------------------------- dispatch

    @Test
    void postStaysSilentWhileDispatchTellsThePublisher() {
        DefaultEventBus bus = new DefaultEventBus("test");
        bus.subscribe(Reward.class, event -> {
            throw new IllegalStateException("delivery failed");
        });

        // The existing contract is untouched: post() swallows and returns the event.
        Reward posted = new Reward();
        assertSame(posted, bus.post(posted));

        // dispatch() is the same delivery with the outcome handed back.
        EventResult<Reward> result = bus.dispatch(new Reward());
        assertTrue(result.hasFailures());
        assertEquals(1, result.handlersInvoked());
        assertEquals("test", result.failures().get(0).ownerId());
        assertEquals(Reward.class, result.failures().get(0).handlerEventType());
    }

    @Test
    void aSuccessfulDispatchReportsNoFailuresAndCountsWhatRan() {
        DefaultEventBus bus = new DefaultEventBus("test");
        bus.subscribe(Reward.class, event -> event.log.add("a"));
        bus.subscribe(Reward.class, event -> event.log.add("b"));

        Reward reward = new Reward();
        EventResult<Reward> result = bus.dispatch(reward);

        assertFalse(result.hasFailures());
        assertEquals(2, result.handlersInvoked());
        assertSame(reward, result.event());
        assertSame(result, result.throwIfFailed(), "nothing to throw returns the result for chaining");
    }

    @Test
    void continueRunsEveryHandlerAndCollectsEveryFailure() {
        DefaultEventBus bus = new DefaultEventBus("test");
        bus.subscribe(Reward.class, EventPriority.FIRST, event -> {
            throw new IllegalStateException("first");
        });
        bus.subscribe(Reward.class, EventPriority.NORMAL, event -> event.log.add("middle"));
        bus.subscribe(Reward.class, EventPriority.LAST, event -> {
            throw new IllegalArgumentException("last");
        });

        Reward reward = new Reward();
        EventResult<Reward> result = bus.dispatch(reward, FailurePolicy.CONTINUE);

        assertEquals(3, result.handlersInvoked(), "a failed handler still ran");
        assertEquals(List.of("middle"), reward.log);
        assertEquals(2, result.failures().size());
        assertEquals("first", result.failures().get(0).cause().getMessage());
        assertEquals("last", result.failures().get(1).cause().getMessage());
    }

    @Test
    void stopHaltsAtTheFirstFailure() {
        DefaultEventBus bus = new DefaultEventBus("test");
        bus.subscribe(Reward.class, EventPriority.FIRST, event -> event.log.add("before"));
        bus.subscribe(Reward.class, EventPriority.NORMAL, event -> {
            throw new IllegalStateException("stop here");
        });
        bus.subscribe(Reward.class, EventPriority.LAST, event -> event.log.add("after"));

        Reward reward = new Reward();
        EventResult<Reward> result = bus.dispatch(reward, FailurePolicy.STOP);

        // For handlers that build on one another: running the later one after the earlier
        // failed would act on a half-done state.
        assertEquals(List.of("before"), reward.log);
        assertEquals(2, result.handlersInvoked());
        assertEquals(1, result.failures().size());
    }

    @Test
    void throwIfFailedTurnsFailuresIntoOneExceptionWithEveryCauseAttached() {
        DefaultEventBus bus = new DefaultEventBus("test");
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");
        bus.subscribe(Reward.class, EventPriority.FIRST, event -> {
            throw first;
        });
        bus.subscribe(Reward.class, EventPriority.LAST, event -> {
            throw second;
        });

        EventDispatchException thrown = assertThrows(EventDispatchException.class,
                () -> bus.dispatch(new Reward()).throwIfFailed());

        assertSame(first, thrown.getCause(), "the first failure is the cause");
        assertSame(second, thrown.getSuppressed()[0], "the rest are suppressed, so none is lost");
        assertEquals(2, thrown.failures().size());
        assertTrue(thrown.getMessage().contains("2 event handlers failed"), thrown.getMessage());
    }

    @Test
    void theCoordinatorPatternFromTheReport() {
        DefaultEventBus bus = new DefaultEventBus("test");
        List<String> session = new ArrayList<>();
        bus.subscribe(Reward.class, event -> {
            throw new IllegalStateException("could not record the delivery");
        });

        // The publisher now knows, and can avoid marking the session complete.
        if (!bus.dispatch(new Reward()).hasFailures()) {
            session.add("completed");
        }

        assertTrue(session.isEmpty());
    }

    // ----------------------------------------------------------------- cancelling

    @Test
    void aFailureDoesNotUndoWhatTheHandlerDidToTheEventFirst() {
        DefaultEventBus bus = new DefaultEventBus("test");
        bus.subscribe(CancellableReward.class, EventPriority.FIRST, event -> {
            event.setCancelled(true);
            throw new IllegalStateException("cancelled, then failed");
        });
        List<String> seen = new ArrayList<>();
        bus.subscribe(CancellableReward.class, EventPriority.NORMAL, event -> seen.add("acts"));

        EventResult<CancellableReward> result = bus.dispatch(new CancellableReward());

        // The bus has no transaction to roll back: the event stays cancelled and the normal
        // rules apply to the handlers after it. The result is how a publisher learns about it.
        assertTrue(result.isCancelled());
        assertTrue(result.hasFailures());
        assertTrue(seen.isEmpty(), "a handler that did not ask for cancelled events is skipped");
    }
}
