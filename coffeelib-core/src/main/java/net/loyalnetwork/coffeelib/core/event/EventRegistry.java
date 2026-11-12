package net.loyalnetwork.coffeelib.core.event;

import net.loyalnetwork.coffeelib.api.event.Cancellable;
import net.loyalnetwork.coffeelib.api.event.EventPriority;
import net.loyalnetwork.coffeelib.api.event.annotation.Subscribe;
import net.loyalnetwork.coffeelib.api.event.exception.EventException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Where handlers actually live, and the part that does the dispatching.
 * <p>
 * Split from {@link DefaultEventBus} because the two have different lifetimes:
 * every owner gets its own bus handle, bound to its id, while the handlers all
 * sit in one registry so that posting from one owner reaches another's
 * handlers. The handle is the public API; this is the shared state behind it.
 * <p>
 * Thread safety: handler lists are copy-on-write, so delivery iterates a
 * snapshot and a handler may register, unregister or post without disturbing
 * the event in flight. {@link #resolved} memoizes the per-event-type lookup
 * (walking supertypes and sorting by priority is not something to redo per
 * post); any mutation clears it wholesale, which is both correct and cheap
 * given how rarely handlers change compared to how often events fly.
 */
final class EventRegistry {

    private static final Logger LOGGER = Logger.getLogger(EventRegistry.class.getName());

    /** Handlers by the exact type they were registered for. */
    private final Map<Class<?>, CopyOnWriteArrayList<Handler>> byEventType = new ConcurrentHashMap<>();

    /** Memoized "every handler that should see this exact event class", in run order. */
    private final Map<Class<?>, List<Handler>> resolved = new ConcurrentHashMap<>();

    /** Registration counter, so handlers of equal priority keep registration order. */
    private long sequence;

    <T> Handler subscribe(String ownerId, Class<T> eventType, EventPriority priority,
                          boolean receiveCancelled, Object listener, Consumer<Object> invoker) {
        Handler handler = new Handler(ownerId, eventType, priority, receiveCancelled, listener, invoker,
                nextSequence());
        byEventType.computeIfAbsent(eventType, type -> new CopyOnWriteArrayList<>()).add(handler);
        resolved.clear();
        return handler;
    }

    private synchronized long nextSequence() {
        return sequence++;
    }

    /**
     * Registers every {@code @Subscribe} method of {@code listener}.
     * <p>
     * Walks up the class hierarchy so a handler inherited from a base class
     * counts, and rejects a bad signature loudly: an annotated method that
     * never fires is the kind of thing someone debugs for an hour.
     */
    void register(String ownerId, Object listener) {
        List<Handler> created = new ArrayList<>();
        for (Class<?> type = listener.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Subscribe subscribe = method.getAnnotation(Subscribe.class);
                if (subscribe == null || method.isSynthetic()) {
                    continue;
                }
                if (method.getParameterCount() != 1) {
                    throw new EventException("@Subscribe method '" + type.getName() + "#" + method.getName()
                            + "' must take exactly one parameter, the event type, but takes "
                            + method.getParameterCount());
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }

                try {
                    method.setAccessible(true);
                } catch (RuntimeException e) {
                    throw new EventException("Could not access @Subscribe method '" + type.getName() + "#"
                            + method.getName() + "'", e);
                }

                Method target = method;
                created.add(subscribe(ownerId, method.getParameterTypes()[0], subscribe.priority(),
                        subscribe.receiveCancelled(), listener, event -> invoke(target, listener, event)));
            }
        }

        if (created.isEmpty()) {
            LOGGER.warning("[CoffeeLib/" + ownerId + "] " + listener.getClass().getName()
                    + " was registered on the event bus but declares no @Subscribe methods");
        }
    }

    private static void invoke(Method method, Object listener, Object event) {
        try {
            method.invoke(listener, event);
        } catch (InvocationTargetException e) {
            // Unwrap so the handler's own failure is what gets logged, not the reflection layer.
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException runtime ? runtime : new EventException(
                    "Handler '" + method.getName() + "' failed", cause);
        } catch (IllegalAccessException e) {
            throw new EventException("Could not call @Subscribe method '" + method.getName() + "'", e);
        }
    }

    /** Drops one handler (a lambda subscription undoing itself). */
    void remove(Handler handler) {
        CopyOnWriteArrayList<Handler> handlers = byEventType.get(handler.eventType());
        if (handlers != null && handlers.remove(handler)) {
            resolved.clear();
        }
    }

    /** Drops every handler {@code register} created for this exact instance. */
    void unregister(String ownerId, Object listener) {
        removeMatching(handler -> handler.ownerId().equals(ownerId) && handler.listener() == listener);
    }

    /** Drops everything one owner registered, by either route. */
    void unregisterAll(String ownerId) {
        removeMatching(handler -> handler.ownerId().equals(ownerId));
    }

    private void removeMatching(java.util.function.Predicate<Handler> condition) {
        boolean changed = false;
        for (CopyOnWriteArrayList<Handler> handlers : byEventType.values()) {
            changed |= handlers.removeIf(condition);
        }
        if (changed) {
            resolved.clear();
        }
    }

    /**
     * Delivers {@code event} to every handler registered for its type or any
     * supertype of it.
     * <p>
     * A handler that throws is logged and skipped; the rest still run. That is
     * deliberate: handlers belong to different owners, and one mod's bug must
     * not swallow another mod's events, nor propagate back into whoever posted.
     */
    void post(Object event) {
        for (Handler handler : resolve(event.getClass())) {
            if (event instanceof Cancellable cancellable
                    && cancellable.isCancelled()
                    && !handler.receiveCancelled()) {
                continue;
            }
            try {
                handler.invoker().accept(event);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.log(Level.WARNING, "[CoffeeLib/" + handler.ownerId() + "] Event handler for "
                        + handler.eventType().getSimpleName() + " failed while handling "
                        + event.getClass().getName(), e);
            }
        }
    }

    /** How many handlers one owner currently has, for diagnostics. */
    int handlerCount(String ownerId) {
        int count = 0;
        for (CopyOnWriteArrayList<Handler> handlers : byEventType.values()) {
            for (Handler handler : handlers) {
                if (handler.ownerId().equals(ownerId)) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<Handler> resolve(Class<?> eventType) {
        return resolved.computeIfAbsent(eventType, type -> {
            List<Handler> matching = new ArrayList<>();
            collectHierarchy(type, matching);
            matching.sort(Comparator.comparingInt((Handler handler) -> handler.priority().ordinal())
                    .thenComparingLong(Handler::sequence));
            return List.copyOf(matching);
        });
    }

    /**
     * Collects handlers for {@code type}, its superclasses and every interface
     * either implements, so subscribing to a supertype or an interface works
     * the way a listener would expect.
     */
    private void collectHierarchy(Class<?> type, List<Handler> into) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            CopyOnWriteArrayList<Handler> handlers = byEventType.get(current);
            if (handlers != null) {
                into.addAll(handlers);
            }
            for (Class<?> implemented : current.getInterfaces()) {
                collectInterface(implemented, into);
            }
        }
    }

    private void collectInterface(Class<?> type, List<Handler> into) {
        CopyOnWriteArrayList<Handler> handlers = byEventType.get(type);
        if (handlers != null) {
            into.addAll(handlers);
        }
        for (Class<?> parent : type.getInterfaces()) {
            collectInterface(parent, into);
        }
    }

    /**
     * One registered handler. {@code listener} is the object {@code register}
     * was called with, kept so {@code unregister} can match by identity; it is
     * null for a lambda subscription, which is undone through its own handle.
     */
    record Handler(String ownerId, Class<?> eventType, EventPriority priority, boolean receiveCancelled,
                   Object listener, Consumer<Object> invoker, long sequence) {
    }
}
