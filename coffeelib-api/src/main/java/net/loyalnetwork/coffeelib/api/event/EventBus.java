package net.loyalnetwork.coffeelib.api.event;

import net.loyalnetwork.coffeelib.api.event.annotation.Subscribe;
import net.loyalnetwork.coffeelib.api.event.exception.EventException;

import java.util.function.Consumer;

/**
 * CoffeeLib's own event bus: one channel every mod and plugin built on the
 * library shares, independent of Forge's {@code IEventBus} and Bukkit's
 * {@code PluginManager}.
 * <p>
 * The point is talking <em>between</em> owners. A platform bus can carry a
 * mod's own events, but it binds the sender and the receiver to that platform:
 * the same feature then needs a Forge event, a NeoForge event and a Bukkit
 * event, each registered a different way, and an event defined by one mod is
 * awkward for another to listen to. Here, one API posts and receives the same
 * plain Java types on every platform, so two mods — or a mod and a plugin
 * sharing code — agree on an event class and nothing else.
 * <p>
 * What a platform entry point hands you is bound to your own mod or plugin id:
 * {@link #unregisterAll()} drops only your handlers, and a failing handler is
 * logged against you. Delivery is not scoped, which is the whole idea — an
 * event posted by one owner reaches every owner's handlers.
 * <p>
 * Two ways to listen, pick per case:
 * <ul>
 *   <li>{@link #subscribe(Class, Consumer)} for a lambda, when you want the
 *       handle back and nothing else;</li>
 *   <li>{@link #register(Object)} for an object whose {@link Subscribe}-annotated
 *       methods are the handlers, when one class groups several.</li>
 * </ul>
 * Handlers run in {@link EventPriority} order, on the thread that posted —
 * there is no queue and no thread hand-off, so a handler doing real work
 * blocks the poster, and anything touching the server's state has to get
 * itself onto the right thread the way that platform expects. Handlers of
 * equal priority run in registration order.
 * <p>
 * A handler that throws does not stop the ones after it: the failure is
 * logged, with the owner it belongs to, and delivery carries on. One mod
 * cannot break another's handlers by being broken itself.
 * <p>
 * Implementations are thread-safe. Registering, unregistering and posting from
 * inside a handler are all allowed; a change made during delivery applies to
 * the next event, not the one in flight.
 */
public interface EventBus {

    /** The mod or plugin id this handle belongs to. */
    String ownerId();

    /**
     * Calls {@code handler} for every event of {@code eventType}, including
     * subtypes, at {@link EventPriority#NORMAL}.
     *
     * @return a handle to undo exactly this subscription
     */
    <T> EventSubscription subscribe(Class<T> eventType, Consumer<? super T> handler);

    /**
     * {@link #subscribe(Class, Consumer)} at an explicit priority.
     * <p>
     * Cancelled events are not delivered here. Use {@link #register(Object)}
     * with {@code @Subscribe(receiveCancelled = true)} for a handler that
     * wants them anyway.
     */
    <T> EventSubscription subscribe(Class<T> eventType, EventPriority priority, Consumer<? super T> handler);

    /**
     * Registers every {@link Subscribe}-annotated method of {@code listener}
     * as a handler for its single parameter's type.
     *
     * @throws EventException if an annotated method does not take exactly one
     *                        parameter — a silent no-op would be worse than a
     *                        failure at registration
     */
    void register(Object listener);

    /**
     * Drops the handlers {@link #register(Object)} created for this exact
     * instance. Unknown instances are ignored.
     */
    void unregister(Object listener);

    /**
     * Drops every handler this owner registered, by either route.
     * <p>
     * Worth calling when your owner goes away — a Paper plugin's
     * {@code onDisable}, say. Handlers hold your objects, and through them
     * your class loader, so leaving them behind after a plugin reload keeps
     * the old copy alive and lets stale handlers keep answering events.
     */
    void unregisterAll();

    /**
     * Delivers {@code event} to every matching handler, in priority order, and
     * returns it.
     * <p>
     * Returning the event is what makes a {@link Cancellable} readable at the
     * call site: {@code if (!bus.post(new MyEvent()).isCancelled()) { ... }}.
     * Matching is by type, so a handler registered for a supertype (or an
     * interface) of {@code event} gets it too.
     */
    <T> T post(T event);
}
