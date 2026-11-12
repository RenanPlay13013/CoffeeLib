package net.loyalnetwork.coffeelib.core.event;

import net.loyalnetwork.coffeelib.api.event.EventBus;
import net.loyalnetwork.coffeelib.api.event.EventPriority;
import net.loyalnetwork.coffeelib.api.event.EventSubscription;

import java.util.function.Consumer;

/**
 * Default {@link EventBus}: one owner's handle onto an {@link EventRegistry}.
 * <p>
 * The handle carries the owner id and nothing else; every handler lives in the
 * registry. Two handles over the same registry therefore see each other's
 * events, which is what makes the bus a channel between mods, while
 * {@link #unregisterAll()} still only touches the calling owner's handlers.
 * <p>
 * Obtained from a platform entry point ({@code CoffeeLib.eventBus(...)}), which
 * binds it to the shared registry under your mod or plugin id. The public
 * constructor here is the other case: a bus of your own, with its own private
 * registry, for events you do not want other owners to see or to be bothered
 * by. Nothing posted on a private bus reaches the shared one.
 */
public final class DefaultEventBus implements EventBus {

    private final EventRegistry registry;
    private final String ownerId;

    /** A private bus: its own registry, isolated from every other owner. */
    public DefaultEventBus(String ownerId) {
        this(new EventRegistry(), ownerId);
    }

    DefaultEventBus(EventRegistry registry, String ownerId) {
        this.registry = registry;
        this.ownerId = ownerId;
    }

    @Override
    public String ownerId() {
        return ownerId;
    }

    @Override
    public <T> EventSubscription subscribe(Class<T> eventType, Consumer<? super T> handler) {
        return subscribe(eventType, EventPriority.NORMAL, handler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> EventSubscription subscribe(Class<T> eventType, EventPriority priority, Consumer<? super T> handler) {
        EventRegistry.Handler registered = registry.subscribe(ownerId, eventType, priority, false, null,
                event -> ((Consumer<Object>) handler).accept(event));
        return new Subscription(registry, registered);
    }

    @Override
    public void register(Object listener) {
        registry.register(ownerId, listener);
    }

    @Override
    public void unregister(Object listener) {
        registry.unregister(ownerId, listener);
    }

    @Override
    public void unregisterAll() {
        registry.unregisterAll(ownerId);
    }

    @Override
    public <T> T post(T event) {
        registry.post(event);
        return event;
    }

    /** How many handlers this owner currently has. Handy in tests and when debugging a leak. */
    public int handlerCount() {
        return registry.handlerCount(ownerId);
    }

    /** Undoes one lambda subscription, and stays harmless when called twice. */
    private static final class Subscription implements EventSubscription {

        private final EventRegistry registry;
        private final EventRegistry.Handler handler;
        private volatile boolean active = true;

        private Subscription(EventRegistry registry, EventRegistry.Handler handler) {
            this.registry = registry;
            this.handler = handler;
        }

        @Override
        public Class<?> eventType() {
            return handler.eventType();
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public synchronized void unsubscribe() {
            if (!active) {
                return;
            }
            active = false;
            registry.remove(handler);
        }
    }
}
