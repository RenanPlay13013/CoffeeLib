package net.loyalnetwork.coffeelib.core.event;

import net.loyalnetwork.coffeelib.api.event.Cancellable;
import net.loyalnetwork.coffeelib.api.event.EventFailure;
import net.loyalnetwork.coffeelib.api.event.EventPriority;
import net.loyalnetwork.coffeelib.api.event.EventResult;
import net.loyalnetwork.coffeelib.api.event.FailurePolicy;
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

    /**
     * What a handler is, to the dispatcher: something that takes the event and
     * may throw anything.
     * <p>
     * It is not a {@code Consumer} because a {@code Consumer} cannot throw a
     * checked exception, and a reflective {@code @Subscribe} call can: its
     * method may declare {@code throws Exception}. Forcing that through a
     * {@code Consumer} meant wrapping the failure in an {@code EventException},
     * which is what made the two ways of registering a handler treat the same
     * error differently - a lambda's {@code AssertionError} sailed past the
     * dispatcher while an annotated method's was wrapped into something the
     * dispatcher happened to catch. Declaring {@code throws Throwable} here lets
     * both routes hand the failure over exactly as thrown, and gives the
     * dispatcher one place to decide what counts as one.
     */
    @FunctionalInterface
    interface Invoker {
        void invoke(Object event) throws Throwable;
    }

    <T> Handler subscribe(String ownerId, Class<T> eventType, EventPriority priority,
                          boolean receiveCancelled, Object listener, Invoker invoker) {
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

    /**
     * Calls an {@code @Subscribe} method and lets whatever it threw through as
     * it was thrown. Reflection reports a thrown exception as an
     * {@link InvocationTargetException} around it; unwrapping that - for
     * everything, not only {@code RuntimeException} - is what makes a handler's
     * failure the same thing however the handler was registered.
     */
    private static void invoke(Method method, Object listener, Object event) throws Throwable {
        try {
            method.invoke(listener, event);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause != null ? cause : e;
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
     * supertype of it, and reports what happened.
     * <p>
     * A handler that throws is logged and recorded, and with
     * {@link FailurePolicy#CONTINUE} the rest still run. That is deliberate:
     * handlers belong to different owners, and one mod's bug must not swallow
     * another mod's events, nor propagate back into whoever posted. What counts
     * as a failure is anything a handler throws, whichever way it was
     * registered, except the JVM's own fatal errors (see {@link #isFatal}).
     * <p>
     * Handlers skipped for a cancelled event are not counted as invoked.
     */
    <T> EventResult<T> dispatch(T event, FailurePolicy policy) {
        List<EventFailure> failures = new ArrayList<>();
        int invoked = 0;

        for (Handler handler : resolve(event.getClass())) {
            if (event instanceof Cancellable cancellable
                    && cancellable.isCancelled()
                    && !handler.receiveCancelled()) {
                continue;
            }

            invoked++;
            try {
                handler.invoker().invoke(event);
            } catch (Throwable thrown) {
                if (isFatal(thrown)) {
                    // Not a handler's bug to contain: the VM itself is in trouble, and nothing
                    // after this can be trusted to run. Let it reach the poster.
                    throw (VirtualMachineError) thrown;
                }

                LOGGER.log(Level.WARNING, "[CoffeeLib/" + handler.ownerId() + "] Event handler for "
                        + handler.eventType().getSimpleName() + " failed while handling "
                        + event.getClass().getName(), thrown);
                failures.add(new EventFailure(handler.ownerId(), handler.eventType(), thrown));

                if (policy == FailurePolicy.STOP) {
                    break;
                }
            }
        }

        return new EventResult<>(event, invoked, failures);
    }

    /**
     * {@code OutOfMemoryError} and the rest of {@link VirtualMachineError}: the
     * runtime saying it cannot continue, as opposed to a handler saying it
     * could not do its job.
     * <p>
     * {@link StackOverflowError} is the exception to the exception. It is a
     * {@code VirtualMachineError} by ancestry, but it is a handler recursing
     * into itself, which is exactly the buggy-handler case the isolation is
     * for, and once the stack has unwound it is perfectly safe to carry on.
     */
    private static boolean isFatal(Throwable thrown) {
        return thrown instanceof VirtualMachineError && !(thrown instanceof StackOverflowError);
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
                   Object listener, Invoker invoker, long sequence) {
    }
}
