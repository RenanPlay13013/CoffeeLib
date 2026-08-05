package net.loyalnetwork.coffeelib.api.di;

import net.loyalnetwork.coffeelib.api.di.annotation.Provide;
import net.loyalnetwork.coffeelib.api.di.annotation.Receive;
import net.loyalnetwork.coffeelib.api.di.exception.AmbiguousProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.MissingProviderException;

/**
 * A small, explicit provider/receiver wiring registry — not a general IoC
 * container. There is no automatic dependency graph between providers: if a
 * {@code @Receive} field needs a provider that hasn't been
 * {@link #register}-ed yet, {@link #wire} fails immediately rather than
 * deferring resolution. Keeping providers registered before their consumers
 * are wired is the caller's responsibility.
 * <p>
 * Platform-agnostic and stateless beyond its own registry — unlike
 * {@code ConfigManager}, there's nothing platform-specific to bind to, so a
 * container isn't obtained through a platform entry point.
 */
public interface ServiceContainer {

    /**
     * Scans {@code providerHost} for {@link Provide}-annotated no-args
     * methods and registers each by its return type (and {@link Provide#value()},
     * if given). A provider method runs at most once — the first
     * successful resolution caches its result for every later lookup.
     */
    void register(Object providerHost);

    /**
     * Scans {@code receiverHost} for {@link Receive}-annotated fields and
     * injects each from a matching registered provider.
     *
     * @throws AmbiguousProviderException if a field names no provider but more than one is registered for its type
     * @throws MissingProviderException   if no registered provider matches a field's type (and name, if given)
     */
    void wire(Object receiverHost);
}
