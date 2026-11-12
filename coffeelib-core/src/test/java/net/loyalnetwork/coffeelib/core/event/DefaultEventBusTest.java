package net.loyalnetwork.coffeelib.core.event;

import net.loyalnetwork.coffeelib.api.event.Cancellable;
import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.event.EventPriority;
import net.loyalnetwork.coffeelib.api.event.EventSubscription;
import net.loyalnetwork.coffeelib.api.event.annotation.Subscribe;
import net.loyalnetwork.coffeelib.api.event.exception.EventException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each test gets its own {@link DefaultEventBus}, which means its own
 * registry — the shared one is covered separately in
 * {@link SharedEventBusTest}, where cross-owner delivery is the point.
 */
class DefaultEventBusTest {

    interface Audited {
    }

    static class BaseEvent {
        final List<String> trace = new ArrayList<>();
    }

    static final class OrderPlaced extends BaseEvent implements Audited {
        String item = "coffee";
    }

    static final class OrderCancelled extends BaseEvent implements Cancellable {
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

    @Test
    void aLambdaHandlerReceivesItsEventAndTheEventComesBack() {
        EventBus bus = new DefaultEventBus("testmod");
        List<String> seen = new ArrayList<>();

        bus.subscribe(OrderPlaced.class, event -> seen.add(event.item));

        OrderPlaced posted = new OrderPlaced();
        assertTrue(bus.post(posted) == posted, "post returns the event so a Cancellable reads at the call site");
        assertEquals(List.of("coffee"), seen);
    }

    @Test
    void handlersRunInPriorityOrderThenRegistrationOrder() {
        EventBus bus = new DefaultEventBus("testmod");

        bus.subscribe(BaseEvent.class, EventPriority.LAST, event -> event.trace.add("last"));
        bus.subscribe(BaseEvent.class, EventPriority.FIRST, event -> event.trace.add("first"));
        bus.subscribe(BaseEvent.class, EventPriority.NORMAL, event -> event.trace.add("normal-a"));
        bus.subscribe(BaseEvent.class, EventPriority.MONITOR, event -> event.trace.add("monitor"));
        bus.subscribe(BaseEvent.class, EventPriority.NORMAL, event -> event.trace.add("normal-b"));

        BaseEvent event = new BaseEvent();
        bus.post(event);

        assertEquals(List.of("first", "normal-a", "normal-b", "last", "monitor"), event.trace);
    }

    @Test
    void aSupertypeOrInterfaceSubscriptionSeesTheSubtype() {
        EventBus bus = new DefaultEventBus("testmod");
        List<String> seen = new ArrayList<>();

        bus.subscribe(BaseEvent.class, event -> seen.add("base"));
        bus.subscribe(Audited.class, event -> seen.add("interface"));
        bus.subscribe(OrderPlaced.class, event -> seen.add("exact"));
        bus.subscribe(OrderCancelled.class, event -> seen.add("unrelated"));

        bus.post(new OrderPlaced());

        // All three match; same priority, so they run in the order they were registered.
        assertEquals(List.of("base", "interface", "exact"), seen);
    }

    @Test
    void annotatedMethodsAreRegisteredAndUnregisteredByInstance() {
        DefaultEventBus bus = new DefaultEventBus("testmod");
        AtomicInteger normal = new AtomicInteger();
        AtomicInteger monitor = new AtomicInteger();

        class Listener {
            @Subscribe
            private void onOrder(OrderPlaced event) {
                normal.incrementAndGet();
            }

            @Subscribe(priority = EventPriority.MONITOR)
            void audit(BaseEvent event) {
                monitor.incrementAndGet();
            }
        }

        Listener listener = new Listener();
        bus.register(listener);
        assertEquals(2, bus.handlerCount(), "both annotated methods, private one included");

        bus.post(new OrderPlaced());
        assertEquals(1, normal.get());
        assertEquals(1, monitor.get());

        bus.unregister(listener);
        bus.post(new OrderPlaced());
        assertEquals(1, normal.get(), "nothing should arrive after unregister");
        assertEquals(0, bus.handlerCount());
    }

    @Test
    void aCancelledEventOnlyReachesHandlersThatAskedForIt() {
        DefaultEventBus bus = new DefaultEventBus("testmod");
        List<String> seen = new ArrayList<>();

        class Listener {
            @Subscribe(priority = EventPriority.FIRST)
            void veto(OrderCancelled event) {
                seen.add("veto");
                event.setCancelled(true);
            }

            @Subscribe(priority = EventPriority.LATE)
            void acts(OrderCancelled event) {
                seen.add("acts");
            }

            @Subscribe(priority = EventPriority.MONITOR, receiveCancelled = true)
            void audits(OrderCancelled event) {
                seen.add("audits");
            }
        }

        bus.register(new Listener());
        OrderCancelled event = bus.post(new OrderCancelled());

        assertTrue(event.isCancelled());
        assertEquals(List.of("veto", "audits"), seen);
    }

    @Test
    void aFailingHandlerDoesNotStopTheOthersOrTheCaller() {
        EventBus bus = new DefaultEventBus("testmod");
        AtomicInteger after = new AtomicInteger();

        bus.subscribe(OrderPlaced.class, EventPriority.FIRST, event -> {
            throw new IllegalStateException("handler is broken");
        });
        bus.subscribe(OrderPlaced.class, EventPriority.LATE, event -> after.incrementAndGet());

        // One owner's bug must not swallow another's events, nor surface in whoever posted.
        bus.post(new OrderPlaced());

        assertEquals(1, after.get());
    }

    @Test
    void anUnsubscribedLambdaStopsReceiving() {
        EventBus bus = new DefaultEventBus("testmod");
        AtomicInteger count = new AtomicInteger();

        EventSubscription subscription = bus.subscribe(OrderPlaced.class, event -> count.incrementAndGet());
        bus.post(new OrderPlaced());

        assertTrue(subscription.isActive());
        assertEquals(OrderPlaced.class, subscription.eventType());

        subscription.unsubscribe();
        subscription.unsubscribe();  // idempotent

        bus.post(new OrderPlaced());
        assertEquals(1, count.get());
        assertFalse(subscription.isActive());
    }

    @Test
    void registeringOrPostingFromInsideAHandlerIsAllowed() {
        EventBus bus = new DefaultEventBus("testmod");
        List<String> seen = new ArrayList<>();

        bus.subscribe(OrderPlaced.class, event -> {
            seen.add("outer");
            // Subscribing mid-delivery must not disturb the event in flight.
            bus.subscribe(OrderPlaced.class, later -> seen.add("added"));
            bus.post(new OrderCancelled());
        });
        bus.subscribe(OrderCancelled.class, event -> seen.add("nested"));

        bus.post(new OrderPlaced());
        assertEquals(List.of("outer", "nested"), seen);

        seen.clear();
        bus.post(new OrderPlaced());
        assertTrue(seen.contains("added"), "the handler added during delivery applies to the next event");
    }

    @Test
    void aBadlyShapedHandlerFailsAtRegistration() {
        EventBus bus = new DefaultEventBus("testmod");

        class TwoParameters {
            @Subscribe
            void onOrder(OrderPlaced event, String extra) {
            }
        }

        // A silently ignored @Subscribe is an hour of debugging; say so instead.
        EventException e = assertThrows(EventException.class, () -> bus.register(new TwoParameters()));
        assertTrue(e.getMessage().contains("exactly one parameter"));
    }

    @Test
    void deliveryIsThreadSafe() throws Exception {
        EventBus bus = new DefaultEventBus("testmod");
        AtomicInteger delivered = new AtomicInteger();
        bus.subscribe(OrderPlaced.class, event -> delivered.incrementAndGet());

        int threads = 8;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    for (int post = 0; post < perThread; post++) {
                        bus.post(new OrderPlaced());
                        // Churn the handler list while events are flying.
                        bus.subscribe(OrderCancelled.class, ignored -> {
                        }).unsubscribe();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "posting should not deadlock");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads * perThread, delivered.get());
    }
}
